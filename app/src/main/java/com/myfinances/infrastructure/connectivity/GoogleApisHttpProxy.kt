package com.jcadenas.xpendz.infrastructure.connectivity

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Mini-proxy CONNECT en loopback para el tráfico HttpURLConnection de
 * Firebase Auth / securetoken / cualquier HTTPS hacia *.googleapis.com.
 * El ProxySelector solo le enruta hosts googleapis; el proxy abre TCP
 * contra las IPs del pool (timeout corto por IP) y reenvía bytes —
 * TLS viaja extremo a extremo (SNI y validación de certificados se
 * hacen del lado del cliente; el proxy no inspecciona nada).
 */
object GoogleApisHttpProxy {

    private const val TAG = "GoogleApisHttpProxy"
    private const val CONNECT_TIMEOUT_MS = 3_000
    private const val HEADER_TIMEOUT_MS = 15_000
    private const val MAX_HEADER_BYTES = 16 * 1024
    private const val PUMP_BUFFER = 16 * 1024

    private val counter = AtomicInteger()
    private val threads = Executors.newCachedThreadPool(
        ThreadFactory { r ->
            Thread(r, "gapis-proxy-${counter.incrementAndGet()}").apply { isDaemon = true }
        }
    )

    @Volatile
    private var server: ServerSocket? = null

    val port: Int
        get() = server?.localPort ?: -1

    @Synchronized
    fun ensureStarted() {
        if (server != null) return
        try {
            val ss = ServerSocket()
            ss.bind(InetSocketAddress("127.0.0.1", 0))
            server = ss
            threads.execute { acceptLoop(ss) }
            Log.d(TAG, "loopback proxy on 127.0.0.1:${ss.localPort}")
        } catch (e: Exception) {
            Log.e(TAG, "proxy start failed", e)
        }
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!ss.isClosed) {
            try {
                val client = ss.accept()
                threads.execute {
                    runCatching { handle(client) }
                        .onFailure { runCatching { client.close() } }
                }
            } catch (e: IOException) {
                if (!ss.isClosed) Log.e(TAG, "accept failed", e)
                return
            }
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = HEADER_TIMEOUT_MS
        val target = parseConnectTarget(readHeader(client.getInputStream()))
        if (target == null) {
            client.getOutputStream().write("HTTP/1.1 501 Not Implemented\r\n\r\n".toHttp())
            client.close()
            return
        }
        val upstream = connectUpstream(target.first, target.second)
        if (upstream == null) {
            client.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toHttp())
            client.close()
            return
        }
        client.soTimeout = 0
        client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toHttp())
        client.getOutputStream().flush()
        relay(client, upstream)
    }

    // Parsea "CONNECT host:port HTTP/1.1\r\nHeader: ...\r\n\r\n".
    internal fun parseConnectTarget(header: String): Pair<String, Int>? {
        val parts = header.lineSequence().firstOrNull()?.split(' ')
            ?: return null
        if (parts.size < 2 || !parts[0].equals("CONNECT", ignoreCase = true)) return null
        val hostport = parts[1]
        val idx = hostport.lastIndexOf(':')
        val host = if (idx >= 0) hostport.substring(0, idx) else hostport
        if (host.isBlank()) return null
        val port = if (idx >= 0) hostport.substring(idx + 1).toIntOrNull() ?: 443 else 443
        return host to port
    }

    private fun readHeader(input: InputStream): String {
        val buf = StringBuilder()
        while (buf.length < MAX_HEADER_BYTES) {
            val b = input.read()
            if (b < 0) break
            buf.append(b.toChar())
            val n = buf.length
            if (n >= 4 && buf.substring(n - 4) == "\r\n\r\n") break
        }
        return buf.toString()
    }

    private fun connectUpstream(host: String, port: Int): Socket? {
        val candidates = GoogleApisIpPool.poolFor(host)
            .map { InetSocketAddress(it, port) }
            .ifEmpty { listOf(InetSocketAddress(host, port)) }
        for (addr in candidates) {
            try {
                val s = Socket()
                s.connect(addr, CONNECT_TIMEOUT_MS)
                return s
            } catch (_: IOException) {
            }
        }
        Log.w(TAG, "upstream connect failed for $host:$port")
        return null
    }

    private fun relay(client: Socket, upstream: Socket) {
        threads.execute {
            pump(client.getInputStream(), upstream.getOutputStream())
            closeQuietly(client)
            closeQuietly(upstream)
        }
        pump(upstream.getInputStream(), client.getOutputStream())
        closeQuietly(client)
        closeQuietly(upstream)
    }

    private fun pump(input: InputStream, output: OutputStream) {
        val buf = ByteArray(PUMP_BUFFER)
        try {
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                output.write(buf, 0, n)
                output.flush()
            }
        } catch (_: IOException) {
        }
    }

    private fun closeQuietly(s: Socket) {
        runCatching { s.close() }
    }

    private fun String.toHttp() = toByteArray(StandardCharsets.US_ASCII)
}

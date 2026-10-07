package com.jcadenas.xpendz.infrastructure.connectivity

import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Instala el blindaje de conectividad Google APIs en el proceso:
 *
 *  - Firestore (gRPC): cubierto por GoogleApisGrpcNameResolverProvider
 *    (registrado vía META-INF/services), que entrega el pool IP fusionado.
 *  - Firebase Auth / securetoken / cualquier HttpURLConnection: cubierto
 *    por este ProxySelector, que enruta solo hosts *.googleapis.com por
 *    el proxy CONNECT en loopback (GoogleApisHttpProxy). El resto del
 *    tráfico sigue por el selector previo / DIRECT.
 *
 * Equivalente Android de GoogleApisFailoverResolver de Desktop: sin IPs
 * hardcodeadas, sin VPN, sin config externa, sin tocar TLS.
 */
object GoogleApisConnectivityShield {

    private const val TAG = "ConnectivityShield"

    @Synchronized
    fun install() {
        GoogleApisHttpProxy.ensureStarted()
        val current = ProxySelector.getDefault()
        if (current !is GoogleApisProxySelector) {
            ProxySelector.setDefault(GoogleApisProxySelector(current))
            Log.d(TAG, "installed: loopback CONNECT for *.googleapis.com")
        }
        // Precalienta el pool DNS para que el primer auth/sync ya tenga
        // IPs alternativas disponibles (corre en daemon, no bloquea).
        Thread({
            runCatching { GoogleApisIpPool.poolFor("identitytoolkit.googleapis.com") }
        }, "gapis-pool-warm").apply { isDaemon = true }.start()
    }
}

internal class GoogleApisProxySelector(
    private val delegate: ProxySelector?
) : ProxySelector() {

    override fun select(uri: URI): List<Proxy> {
        val host = uri.host
        val proxyPort = GoogleApisHttpProxy.port
        return if (host != null && proxyPort > 0 && GoogleApisIpPool.isShieldedHost(host)) {
            listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyPort)))
        } else {
            delegate?.select(uri) ?: listOf(Proxy.NO_PROXY)
        }
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        delegate?.connectFailed(uri, sa, ioe)
    }
}

package com.jcadenas.xpendz.infrastructure.connectivity

import android.util.Log
import java.net.InetAddress
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pool de IPs alternativas para hosts *.googleapis.com — equivalente Android
 * de GoogleApisFailoverResolver (Desktop v1.0.06).
 *
 * Ciertas redes filtran el pool de IPs que el DNS asigna al host pedido,
 * pero el edge anycast de Google sirve cualquier servicio googleapis desde
 * cualquier IP googleapis. Se resuelven los hosts hermanos en paralelo y
 * se fusionan las direcciones deduplicadas — sin IPs hardcodeadas, sin
 * VPN, sin configuración externa.
 */
object GoogleApisIpPool {

    private const val TAG = "GoogleApisIpPool"
    private const val POOL_TTL_MS = 60_000L
    private const val DNS_TIMEOUT_MS = 4_000L

    // Hosts googleapis (y auth de Google) que la app puede tocar; cada uno
    // actúa también como fuente de IPs hermanas del edge de Google.
    private val SIBLING_HOSTS = listOf(
        "firestore.googleapis.com",
        "identitytoolkit.googleapis.com",
        "securetoken.googleapis.com",
        "www.googleapis.com",
        "oauth2.googleapis.com",
        "firebaseinstallations.googleapis.com",
        "firebaselogging.googleapis.com",
        "accounts.google.com"
    )

    private val counter = AtomicInteger()
    private val executor = Executors.newFixedThreadPool(
        SIBLING_HOSTS.size,
        ThreadFactory { r ->
            Thread(r, "gapis-dns-${counter.incrementAndGet()}").apply { isDaemon = true }
        }
    )

    private val lock = Any()

    @Volatile
    private var cachedPool: List<InetAddress> = emptyList()

    @Volatile
    private var cachedAtMs: Long = 0L

    fun isShieldedHost(host: String?): Boolean {
        val h = host?.lowercase()?.trimEnd('.') ?: return false
        return h == "googleapis.com" || h.endsWith(".googleapis.com") || h == "accounts.google.com"
    }

    /**
     * IPs candidatas para `host`: primero el pool fusionado de hermanos
     * (las alternativas del edge) y al final las del propio host — el
     * caso típico del bloqueo es que el pool del DNS propio no responde.
     * Lista vacía si nada resolvió: el caller debe degradar al DNS normal.
     */
    fun poolFor(host: String): List<InetAddress> {
        if (!isShieldedHost(host)) return emptyList()
        val pool = mergedPool()
        val own = resolve(host)
        if (pool.isEmpty()) return own
        val seen = pool.mapTo(HashSet()) { it.hostAddress.orEmpty() }
        val ordered = ArrayList<InetAddress>(pool.size + own.size)
        ordered += pool
        own.filterTo(ordered) { it.hostAddress !in seen }
        return ordered
    }

    private fun mergedPool(): List<InetAddress> {
        val now = System.currentTimeMillis()
        if (cachedPool.isNotEmpty() && now - cachedAtMs < POOL_TTL_MS) return cachedPool
        synchronized(lock) {
            val again = System.currentTimeMillis()
            if (cachedPool.isNotEmpty() && again - cachedAtMs < POOL_TTL_MS) return cachedPool
            val merged = resolveSiblings()
            if (merged.isNotEmpty()) {
                cachedPool = merged
                cachedAtMs = System.currentTimeMillis()
            }
            return merged.ifEmpty { cachedPool }
        }
    }

    private fun resolveSiblings(): List<InetAddress> {
        val results = ConcurrentLinkedQueue<InetAddress>()
        val futures = SIBLING_HOSTS.map { h ->
            executor.submit(Callable {
                resolve(h).forEach { results.add(it) }
            })
        }
        for (f in futures) {
            try {
                f.get(DNS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
            }
        }
        val seen = LinkedHashSet<String>()
        val merged = ArrayList<InetAddress>(results.size)
        for (a in results) {
            if (seen.add(a.hostAddress.orEmpty())) merged += a
        }
        Log.d(TAG, "merged pool: ${merged.size} addresses")
        return merged
    }

    private fun resolve(host: String): List<InetAddress> = try {
        InetAddress.getAllByName(host).toList()
    } catch (_: Exception) {
        emptyList()
    }
}

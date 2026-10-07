package com.jcadenas.xpendz.infrastructure.connectivity

import android.util.Log
import io.grpc.EquivalentAddressGroup
import io.grpc.NameResolver
import io.grpc.NameResolverProvider
import io.grpc.Status
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.Executor

/**
 * NameResolverProvider para gRPC (Firestore): para targets dns: de hosts
 * *.googleapis.com devuelve las IPs del pool fusionado de GoogleApisIpPool;
 * el resto de hosts se resuelven por el DNS del sistema. Prioridad 6 —
 * por encima de DnsNameResolverProvider (5) — para todos los targets dns:.
 * TLS/SNI queda intacto: el canal sigue autenticando el hostname real.
 */
class GoogleApisGrpcNameResolverProvider : NameResolverProvider() {

    override fun newNameResolver(targetUri: URI, args: NameResolver.Args): NameResolver? {
        val endpoint = targetUri.path?.removePrefix("/")?.takeIf { it.isNotBlank() }
            ?: targetUri.authority
            ?: return null
        val host = endpoint.substringBeforeLast(':')
        val port = endpoint.substringAfterLast(':', "443").toIntOrNull() ?: 443
        return PoolNameResolver(host, port, args.offloadExecutor)
    }

    override fun getDefaultScheme(): String = "dns"

    override fun priority(): Int = 6

    override fun isAvailable(): Boolean = true

    private class PoolNameResolver(
        private val host: String,
        private val port: Int,
        offloadExecutor: Executor?
    ) : NameResolver() {

        private val executor = offloadExecutor ?: Executor { it.run() }

        @Volatile
        private var listener: Listener2? = null

        @Volatile
        private var stopped = false

        override fun getServiceAuthority(): String = host

        override fun start(listener: Listener2) {
            this.listener = listener
            refresh()
        }

        override fun refresh() {
            val l = listener ?: return
            executor.execute {
                if (stopped) return@execute
                try {
                    val groups = resolveGroups()
                    if (stopped) return@execute
                    if (groups.isEmpty()) {
                        l.onError(Status.UNAVAILABLE.withDescription("no addresses for $host"))
                    } else {
                        l.onResult(
                            ResolutionResult.newBuilder()
                                .setAddresses(groups)
                                .build()
                        )
                    }
                } catch (e: Exception) {
                    l.onError(Status.UNAVAILABLE.withCause(e))
                }
            }
        }

        private fun resolveGroups(): List<EquivalentAddressGroup> {
            val pool = GoogleApisIpPool.poolFor(host)
            val addrs = pool.ifEmpty {
                try {
                    InetAddress.getAllByName(host).toList()
                } catch (e: Exception) {
                    Log.w(TAG, "DNS failed for $host", e)
                    emptyList()
                }
            }
            return addrs.map { EquivalentAddressGroup(InetSocketAddress(it, port)) }
        }

        override fun shutdown() {
            stopped = true
            listener = null
        }

        private companion object {
            const val TAG = "GoogleApisGrpcResolver"
        }
    }
}

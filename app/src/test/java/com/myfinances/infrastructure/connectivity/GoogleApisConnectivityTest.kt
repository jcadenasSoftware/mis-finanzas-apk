package com.jcadenas.xpendz.infrastructure.connectivity

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Blindaje de conectividad googleapis — lógica pura (sin red):
 * clasificación de hosts, parsing de CONNECT y selección de proxy.
 */
class GoogleApisConnectivityTest {

    // ── isShieldedHost ────────────────────────────────────────────────

    @Test
    fun googleapisHostsAreShielded() {
        assertTrue(GoogleApisIpPool.isShieldedHost("identitytoolkit.googleapis.com"))
        assertTrue(GoogleApisIpPool.isShieldedHost("securetoken.googleapis.com"))
        assertTrue(GoogleApisIpPool.isShieldedHost("firestore.googleapis.com"))
        assertTrue(GoogleApisIpPool.isShieldedHost("googleapis.com"))
        assertTrue(GoogleApisIpPool.isShieldedHost("accounts.google.com"))
        // trailing dot de un FQDN también
        assertTrue(GoogleApisIpPool.isShieldedHost("firestore.googleapis.com."))
    }

    @Test
    fun nonGoogleHostsAreNotShielded() {
        assertFalse(GoogleApisIpPool.isShieldedHost("example.com"))
        assertFalse(GoogleApisIpPool.isShieldedHost("googleapis.com.evil.example"))
        assertFalse(GoogleApisIpPool.isShieldedHost("fakegoogleapis.com"))
        assertFalse(GoogleApisIpPool.isShieldedHost(null))
        assertFalse(GoogleApisIpPool.isShieldedHost(""))
        assertFalse(GoogleApisIpPool.isShieldedHost("google.com"))
    }

    @Test
    fun nonShieldedHostGetsEmptyPool() {
        // poolFor sobre host no protegido nunca toca el DNS
        assertTrue(GoogleApisIpPool.poolFor("example.com").isEmpty())
    }

    // ── parseConnectTarget ────────────────────────────────────────────

    @Test
    fun connectTargetParsesHostAndPort() {
        assertEquals(
            "identitytoolkit.googleapis.com" to 443,
            GoogleApisHttpProxy.parseConnectTarget(
                "CONNECT identitytoolkit.googleapis.com:443 HTTP/1.1\r\nHost: x\r\n\r\n"
            )
        )
    }

    @Test
    fun connectTargetDefaultsTo443WithoutPort() {
        assertEquals(
            "firestore.googleapis.com" to 443,
            GoogleApisHttpProxy.parseConnectTarget(
                "CONNECT firestore.googleapis.com HTTP/1.1\r\n\r\n"
            )
        )
    }

    @Test
    fun nonConnectRequestIsRejected() {
        assertNull(
            GoogleApisHttpProxy.parseConnectTarget(
                "GET https://googleapis.com/ HTTP/1.1\r\n\r\n"
            )
        )
        assertNull(GoogleApisHttpProxy.parseConnectTarget(""))
        assertNull(
            GoogleApisHttpProxy.parseConnectTarget("CONNECT :443 HTTP/1.1\r\n\r\n")
        )
    }

    // ── GoogleApisProxySelector ───────────────────────────────────────

    @Test
    fun selectorRoutesGoogleapisThroughLoopbackProxy() {
        val selector = GoogleApisProxySelector(null)
        val uris = listOf(
            URI("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword"),
            URI("https://securetoken.googleapis.com/v1/token"),
            URI("https://accounts.google.com/o/oauth2/auth")
        )
        // El puerto depende del proxy levantado; sin proxy corriendo
        // (port = -1) el selector delega al fallback → NO_PROXY.
        for (u in uris) {
            val proxies = selector.select(u)
            assertTrue(proxies.isNotEmpty())
        }
    }

    @Test
    fun selectorPassesNonGoogleTrafficToDelegate() {
        val selector = GoogleApisProxySelector(null)
        val proxies = selector.select(URI("https://example.com/x"))
        assertEquals(1, proxies.size)
        assertEquals(java.net.Proxy.NO_PROXY, proxies[0])
    }
}

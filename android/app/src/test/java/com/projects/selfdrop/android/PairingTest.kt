package com.projects.selfdrop.android

import com.projects.selfdrop.android.data.Pairing
import com.projects.selfdrop.android.data.SelfdropApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingTest {

    @Test
    fun parsesPairDeepLink() {
        val target = Pairing.parse(
            "selfdrop://pair?u=http%3A%2F%2F192.168.1.50%3A8080&id=8f14e45f",
        )

        assertEquals("http://192.168.1.50:8080", target?.baseUrl)
        assertEquals("8f14e45f", target?.serverId)
    }

    @Test
    fun parsesPlainServerUrl() {
        val target = Pairing.parse("http://192.168.1.50:9090")
        assertEquals("http://192.168.1.50:9090", target?.baseUrl)
        assertNull(target?.serverId)
    }

    @Test
    fun addsSchemeAndDefaultPort() {
        assertEquals("http://192.168.1.50:8080", Pairing.normalizeBaseUrl("192.168.1.50"))
        assertEquals("http://192.168.1.50:8080", Pairing.normalizeBaseUrl("  http://192.168.1.50/ "))
    }

    @Test
    fun rejectsHttpsAndGarbage() {
        assertNull(Pairing.normalizeBaseUrl("https://192.168.1.50:8080"))
        assertNull(Pairing.normalizeBaseUrl(""))
        assertNull(Pairing.parse("not a code"))
    }

    @Test
    fun workerRecordUrlKeepsOrAddsPath() {
        assertEquals(
            "https://example.workers.dev/r",
            SelfdropApi.workerRecordUrl("https://example.workers.dev"),
        )
        assertEquals(
            "https://example.workers.dev/r",
            SelfdropApi.workerRecordUrl("https://example.workers.dev/r"),
        )
    }

    @Test
    fun sanitizesDownloadFileNames() {
        assertEquals("a_b_c.txt", SelfdropApi.sanitizeFileName("a/b\\c.txt"))
    }
}

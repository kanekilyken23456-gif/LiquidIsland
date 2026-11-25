package com.akira.island.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class NetworkBoundsTest {
    @Test fun oversizedAndTruncatedBodiesAreRejectedBeforeTheyFillStorage() {
        val output = ByteArrayOutputStream()
        assertTrue(runCatching { ByteArrayInputStream(ByteArray(100)).copyBoundedTo(output, 40) }.isFailure)
        assertTrue(output.size() <= 40)
        assertTrue(runCatching { ByteArrayInputStream(ByteArray(20)).copyBoundedTo(output, 100, 40) }.isFailure)
        assertTrue(runCatching { ByteArrayInputStream(ByteArray(50)).copyBoundedTo(output, 100, 40) }.isFailure)
    }
    @Test fun normalUtf8AndExactLengthBodiesAreAccepted() {
        val body = "Погода".toByteArray()
        assertEquals("Погода", ByteArrayInputStream(body).readUtf8Bounded(body.size))
        val output = ByteArrayOutputStream()
        assertEquals(body.size.toLong(), ByteArrayInputStream(body).copyBoundedTo(output, 100, body.size.toLong()))
        assertArrayEquals(body, output.toByteArray())
    }
    @Test fun redirectPolicyRejectsHttpCredentialsOtherOriginsAndPorts() {
        val original = "https://lrclib.net/api/search"
        assertTrue(sameHttpsOrigin(original, "https://lrclib.net/api/get?q=test"))
        for (url in listOf("http://lrclib.net/get", "https://evil.test/get", "https://lrclib.net:8443/get", "https://x@lrclib.net/get", "https://lrclib.net.evil.test/get"))
            assertFalse(url, sameHttpsOrigin(original, url))
    }
}

package com.zeus.companion

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException

class CompanionHttpTest {
    private fun parse(text: String) = CompanionHttp.read(ByteArrayInputStream(text.toByteArray()))

    @Test fun readsAuthenticatedRequestAndExactBody() {
        val request = parse("POST /launch HTTP/1.1\r\nHost: 10.0.0.2\r\nAuthorization: Bearer token\r\nContent-Length: 2\r\n\r\n{}")
        assertEquals("POST", request.method)
        assertEquals("/launch", request.path)
        assertEquals("Bearer token", request.authorization)
        assertArrayEquals("{}".toByteArray(), request.body)
    }
    @Test fun rejectsChunkingDuplicateAuthorizationAndConflictingLengths() {
        listOf("Transfer-Encoding: chunked\r\n", "Authorization: A\r\nAuthorization: B\r\n",
            "Content-Length: 0\r\nContent-Length: 1\r\n", "Content-Length: -1\r\n", "Content-Length: 2049\r\n").forEach {
            assertThrows(Exception::class.java) { parse("POST /launch HTTP/1.1\r\n$it\r\n") }
        }
    }
    @Test fun rejectsOversizedHeadersAndIncompleteBodies() {
        assertThrows(IllegalArgumentException::class.java) { parse("GET /apps HTTP/1.1\r\nX-Large: ${"x".repeat(9000)}\r\n\r\n") }
        assertThrows(EOFException::class.java) { parse("POST /launch HTTP/1.1\r\nContent-Length: 5\r\n\r\n{}") }
    }
    @Test fun missingAuthorizationRemainsUnauthenticated() {
        assertEquals("", parse("GET /apps HTTP/1.1\r\nHost: 10.0.0.2\r\n\r\n").authorization)
    }
}

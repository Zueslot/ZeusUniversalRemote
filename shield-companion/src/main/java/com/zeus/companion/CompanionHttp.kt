package com.zeus.companion

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.util.Locale

internal data class CompanionRequest(val method: String, val path: String, val authorization: String, val body: ByteArray)

/** One bounded HTTP/1.1 request per TLS connection. Chunked bodies and duplicate headers are rejected. */
internal object CompanionHttp {
    fun read(input: InputStream): CompanionRequest {
        val header = ByteArrayOutputStream()
        var tail = 0
        while (header.size() < 8192) {
            val next = input.read()
            if (next < 0) throw EOFException()
            header.write(next)
            tail = (tail shl 8) or next
            if (tail == 0x0d0a0d0a) break
        }
        require(tail == 0x0d0a0d0a) { "Headers too large" }
        val lines = header.toString("US-ASCII").split("\r\n")
        val first = lines.first().split(' ')
        require(first.size == 3 && first[2] == "HTTP/1.1" && first[0] in listOf("GET", "POST"))
        val headers = mutableMapOf<String, String>()
        lines.drop(1).filter { it.isNotEmpty() }.forEach { line ->
            val separator = line.indexOf(':')
            require(separator > 0)
            val key = line.substring(0, separator).lowercase(Locale.ROOT)
            require(key !in headers) { "Duplicate header" }
            headers[key] = line.substring(separator + 1).trim()
        }
        require("transfer-encoding" !in headers)
        val size = headers["content-length"]?.toIntOrNull() ?: if ("content-length" in headers) error("Invalid length") else 0
        require(size in 0..2048)
        val body = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = input.read(body, offset, size - offset)
            if (count < 0) throw EOFException()
            offset += count
        }
        return CompanionRequest(first[0], first[1], headers["authorization"].orEmpty(), body)
    }
}

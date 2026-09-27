package app.taho.browser.testsupport.http

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

data class FixtureRequest(
    val method: String,
    val path: String,
    val query: String?,
    val bodyUtf8: String,
)

class ControlledHttpFixtureServer : AutoCloseable {
    private val running = AtomicBoolean(true)
    private val server = ServerSocket(0, 16, InetAddress.getByName(LOOPBACK_HOST))
    private val observed = CopyOnWriteArrayList<FixtureRequest>()
    private val acceptThread = thread(
        start = true,
        isDaemon = true,
        name = "taho-controlled-http-fixture",
    ) { acceptLoop() }

    val port: Int get() = server.localPort
    val baseUrl: String get() = "http://" + LOOPBACK_HOST + ":" + port

    fun url(path: String): String {
        require(path.startsWith("/")) { "Fixture path must be absolute." }
        return baseUrl + path
    }

    fun observedRequests(): List<FixtureRequest> = observed.toList()

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        runCatching { server.close() }
        acceptThread.join(CLOSE_JOIN_MS)
    }

    private fun acceptLoop() {
        while (running.get()) {
            val socket = try {
                server.accept()
            } catch (_: SocketException) {
                break
            }
            socket.use(::handle)
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = SOCKET_TIMEOUT_MS
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        val requestLine = readAsciiLine(input) ?: return
        val parts = requestLine.split(' ', limit = 3)
        if (parts.size < 2) {
            writeResponse(output, 400, "Bad Request", "text/plain", "bad request")
            return
        }

        val method = parts[0]
        val target = parts[1]
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readAsciiLine(input) ?: break
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase()] =
                    line.substring(separator + 1).trim()
            }
        }

        val declaredLength = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        if (declaredLength > MAX_BODY_BYTES) {
            writeResponse(output, 413, "Payload Too Large", "text/plain", "fixture body too large")
            return
        }

        val bodyBytes = ByteArray(declaredLength)
        var offset = 0
        while (offset < declaredLength) {
            val count = input.read(bodyBytes, offset, declaredLength - offset)
            if (count < 0) break
            offset += count
        }

        val uri = runCatching { URI("http://fixture" + target) }.getOrNull()
        if (uri == null) {
            writeResponse(output, 400, "Bad Request", "text/plain", "invalid target")
            return
        }

        observed += FixtureRequest(
            method = method,
            path = uri.path,
            query = uri.rawQuery,
            bodyUtf8 = bodyBytes.copyOf(offset).toString(StandardCharsets.UTF_8),
        )

        when (uri.path) {
            "/health" -> if (method == "GET") {
                writeResponse(output, 200, "OK", "application/json", """{"ok":true}""")
            } else {
                writeResponse(output, 405, "Method Not Allowed", "text/plain", "method")
            }

            "/api/get" -> if (method == "GET") {
                writeResponse(
                    output, 200, "OK", "application/json",
                    """{"fixture":"get","ok":true}""",
                )
            } else {
                writeResponse(output, 405, "Method Not Allowed", "text/plain", "method")
            }

            "/api/json" -> if (method == "POST") {
                writeResponse(
                    output, 201, "Created", "application/json",
                    """{"fixture":"json","accepted":true}""",
                )
            } else {
                writeResponse(output, 405, "Method Not Allowed", "text/plain", "method")
            }

            "/redirect" -> writeResponse(
                output, 302, "Found", "text/plain", "redirect",
                extraHeaders = listOf("Location" to "/api/get"),
            )

            "/fail" -> writeResponse(
                output, 503, "Service Unavailable", "application/json",
                """{"fixture":"failure"}""",
            )

            else -> writeResponse(output, 404, "Not Found", "text/plain", "not found")
        }
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() <= MAX_LINE_BYTES) {
            val value = input.read()
            if (value < 0) {
                return if (bytes.size() == 0) null
                else bytes.toString(StandardCharsets.US_ASCII)
            }
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes.write(value)
        }
        require(bytes.size() <= MAX_LINE_BYTES) { "Fixture request line/header exceeded limit." }
        return bytes.toString(StandardCharsets.US_ASCII)
    }

    private fun writeResponse(
        output: BufferedOutputStream,
        status: Int,
        reason: String,
        contentType: String,
        body: String,
        extraHeaders: List<Pair<String, String>> = emptyList(),
    ) {
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 " + status + " " + reason + "\r\n")
            append("Content-Type: " + contentType + "\r\n")
            append("Content-Length: " + bodyBytes.size + "\r\n")
            append("Connection: close\r\n")
            extraHeaders.forEach { (name, value) ->
                append(name + ": " + value + "\r\n")
            }
            append("\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)
        output.write(headers)
        output.write(bodyBytes)
        output.flush()
    }

    companion object {
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val MAX_BODY_BYTES = 64 * 1024
        private const val MAX_LINE_BYTES = 8 * 1024
        private const val SOCKET_TIMEOUT_MS = 2_000
        private const val CLOSE_JOIN_MS = 1_000L
    }
}

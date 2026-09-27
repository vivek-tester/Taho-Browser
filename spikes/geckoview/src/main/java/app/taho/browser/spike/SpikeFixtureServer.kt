package app.taho.browser.spike

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loopback-only HTTP fixture for the M1 GeckoView capability probes.
 *
 * It serves synthetic, non-secret traffic so attribution and field availability
 * can be measured without touching user data.
 */
class SpikeFixtureServer : Closeable {
    private data class Request(
        val method: String,
        val target: String,
        val headers: Map<String, String>,
    )

    private val running = AtomicBoolean(false)
    private val workers: ExecutorService = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    val port: Int
        get() = checkNotNull(serverSocket) { "fixture server not started" }.localPort

    val baseUrl: String
        get() = "http://127.0.0.1:$port"

    fun start() {
        if (!running.compareAndSet(false, true)) return

        val server = ServerSocket(
            0,
            50,
            InetAddress.getByName("127.0.0.1"),
        )
        serverSocket = server
        acceptThread = Thread(
            {
                while (running.get()) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    workers.execute { handle(socket) }
                }
            },
            "taho-m1-fixture-accept",
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun pageUrl(tabId: String): String =
        when (tabId) {
            "A" -> "$baseUrl/tab-a"
            "B" -> "$baseUrl/tab-b"
            "P" -> "$baseUrl/tab-p"
            else -> "$baseUrl/extra-" + tabId.removePrefix("X")
        }

    fun redirectUrl(tabId: String): String =
        when (tabId) {
            "A" -> "$baseUrl/redirect-a?tab=A"
            "B" -> "$baseUrl/redirect-b?tab=B"
            "P" -> "$baseUrl/redirect-p?tab=P"
            else -> "$baseUrl/api-json?tab=" + safeMarker(tabId)
        }

    fun safeTagForUrl(raw: String): String {
        val parsed = runCatching { URI(raw) }.getOrNull() ?: return "non-fixture"
        if (parsed.host != "127.0.0.1" && parsed.host != "localhost") {
            return "non-fixture"
        }
        val path = parsed.path ?: return "fixture"
        return if (SAFE_PATH.matches(path)) path else "fixture"
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = SOCKET_TIMEOUT_MS
            val input = client.getInputStream()
            val output = client.getOutputStream()
            val request = readRequest(input) ?: return
            val path = request.target.substringBefore('?')

            if (path == "/ws" && isWebSocketUpgrade(request)) {
                writeWebSocketHandshake(output, request)
                return
            }

            when {
                path == "/tab-a" -> writeHtml(output, pageHtml("A"))
                path == "/tab-b" -> writeHtml(output, pageHtml("B"))
                path == "/tab-p" -> writeHtml(output, pageHtml("P"))
                EXTRA_PATH.matches(path) -> {
                    val marker = "X" + path.substringAfter("/extra-")
                    writeHtml(output, pageHtml(marker))
                }

                path == "/redirect-a" ->
                    writeRedirect(output, "/api-json?tab=A&redirect=1")
                path == "/redirect-b" ->
                    writeRedirect(output, "/api-json?tab=B&redirect=1")
                path == "/redirect-p" ->
                    writeRedirect(output, "/api-json?tab=P&redirect=1")

                path == "/api-json" || path == "/graphql" || path == "/multipart" ||
                    path == "/form" || path == "/bg" ->
                    writeResponse(
                        output = output,
                        status = "200 OK",
                        contentType = "application/json; charset=utf-8",
                        body = """{"fixture":true}""".encodeToByteArray(),
                    )

                path == "/beacon" ->
                    writeResponse(
                        output = output,
                        status = "204 No Content",
                        contentType = "text/plain",
                        body = byteArrayOf(),
                    )

                path == "/prefetch.txt" ->
                    writeResponse(
                        output = output,
                        status = "200 OK",
                        contentType = "text/plain; charset=utf-8",
                        body = "prefetch-fixture".encodeToByteArray(),
                    )

                path == "/preload.css" ->
                    writeResponse(
                        output = output,
                        status = "200 OK",
                        contentType = "text/css; charset=utf-8",
                        body = "body{outline:0}".encodeToByteArray(),
                    )

                path == "/sw.js" ->
                    writeResponse(
                        output = output,
                        status = "200 OK",
                        contentType = "application/javascript; charset=utf-8",
                        body = (
                            "self.addEventListener('install',e=>self.skipWaiting());" +
                                "self.addEventListener('activate',e=>e.waitUntil(self.clients.claim()));"
                            ).encodeToByteArray(),
                    )

                path == "/sse" ->
                    writeResponse(
                        output = output,
                        status = "200 OK",
                        contentType = "text/event-stream; charset=utf-8",
                        body = "data: fixture\n\n".encodeToByteArray(),
                        extraHeaders = mapOf("Cache-Control" to "no-cache"),
                    )

                else ->
                    writeResponse(
                        output = output,
                        status = "404 Not Found",
                        contentType = "text/plain; charset=utf-8",
                        body = "fixture-not-found".encodeToByteArray(),
                    )
            }
        }
    }

    private fun pageHtml(marker: String): ByteArray {
        val encoded = safeMarker(marker)
        val redirectPath = when (encoded) {
            "A" -> "/redirect-a?tab=A"
            "B" -> "/redirect-b?tab=B"
            "P" -> "/redirect-p?tab=P"
            else -> "/api-json?tab=$encoded&redirect=1"
        }

        return """
            <!doctype html>
            <html>
              <head>
                <meta charset="utf-8">
                <title>Taho M1 $encoded</title>
                <link rel="prefetch" href="/prefetch.txt?tab=$encoded">
                <link rel="preload" as="style" href="/preload.css?tab=$encoded">
              </head>
              <body>
                <h1>Taho M1 fixture $encoded</h1>
                <script>
                (() => {
                  const tab = "$encoded";
                  const swallow = (p) => p && p.catch ? p.catch(() => undefined) : undefined;

                  swallow(fetch('/api-json?tab=' + tab));
                  swallow(fetch('/api-json?tab=' + tab, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({fixture: tab})
                  }));

                  const form = new URLSearchParams();
                  form.set('fixture', tab);
                  swallow(fetch('/form?tab=' + tab, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                    body: form.toString()
                  }));

                  const multipart = new FormData();
                  multipart.append('fixture', tab);
                  multipart.append('file', new Blob(['fixture-bytes']), 'fixture.txt');
                  swallow(fetch('/multipart?tab=' + tab, {
                    method: 'POST',
                    body: multipart
                  }));

                  swallow(fetch('/graphql?tab=' + tab, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({query: '{ fixture }'})
                  }));

                  swallow(fetch("$redirectPath"));

                  if (navigator.sendBeacon) {
                    navigator.sendBeacon('/beacon?tab=' + tab, 'fixture=' + tab);
                  }

                  if ('serviceWorker' in navigator) {
                    swallow(navigator.serviceWorker.register('/sw.js'));
                  }

                  try {
                    const sse = new EventSource('/sse?tab=' + tab);
                    sse.onmessage = () => sse.close();
                    sse.onerror = () => sse.close();
                  } catch (_) {}

                  try {
                    const ws = new WebSocket('ws://127.0.0.1:$port/ws?tab=' + tab);
                    ws.onopen = () => ws.close();
                  } catch (_) {}

                  swallow(fetch('http://127.0.0.1:1/failed?tab=' + tab));

                  let n = 0;
                  setInterval(() => {
                    swallow(fetch('/bg?tab=' + tab + '&n=' + (n++)));
                  }, 800);
                })();
                </script>
              </body>
            </html>
        """.trimIndent().encodeToByteArray()
    }

    private fun readRequest(input: InputStream): Request? {
        val headerBytes = ByteArrayOutputStream()
        var state = 0

        while (headerBytes.size() < MAX_HEADER_BYTES) {
            val next = input.read()
            if (next == -1) return null
            headerBytes.write(next)

            state = when {
                state == 0 && next == '\r'.code -> 1
                state == 1 && next == '\n'.code -> 2
                state == 2 && next == '\r'.code -> 3
                state == 3 && next == '\n'.code -> 4
                next == '\r'.code -> 1
                else -> 0
            }

            if (state == 4) break
        }

        if (state != 4) return null

        val raw = headerBytes.toString(StandardCharsets.ISO_8859_1.name())
        val lines = raw.split("\r\n")
        val requestLine = lines.firstOrNull()?.split(' ') ?: return null
        if (requestLine.size < 2) return null

        val headers = buildMap {
            lines.drop(1).forEach { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return@forEach
                val name = line.substring(0, separator)
                    .trim()
                    .lowercase(Locale.ROOT)
                val value = line.substring(separator + 1).trim()
                put(name, value)
            }
        }

        val declaredBody = headers["content-length"]
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 0
        drainBody(input, declaredBody.coerceAtMost(MAX_BODY_DRAIN_BYTES))

        return Request(
            method = requestLine[0],
            target = requestLine[1],
            headers = headers,
        )
    }

    private fun drainBody(input: InputStream, bytes: Int) {
        var remaining = bytes
        val buffer = ByteArray(8 * 1024)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            remaining -= read
        }
    }

    private fun writeHtml(output: OutputStream, body: ByteArray) {
        writeResponse(
            output = output,
            status = "200 OK",
            contentType = "text/html; charset=utf-8",
            body = body,
            extraHeaders = mapOf(
                "Cache-Control" to "no-store",
                "Service-Worker-Allowed" to "/",
            ),
        )
    }

    private fun writeRedirect(output: OutputStream, location: String) {
        writeResponse(
            output = output,
            status = "302 Found",
            contentType = "text/plain; charset=utf-8",
            body = "redirect".encodeToByteArray(),
            extraHeaders = mapOf("Location" to location),
        )
    }

    private fun writeResponse(
        output: OutputStream,
        status: String,
        contentType: String,
        body: ByteArray,
        extraHeaders: Map<String, String> = emptyMap(),
    ) {
        val head = buildString {
            append("HTTP/1.1 ")
            append(status)
            append("\r\n")
            append("Content-Type: ")
            append(contentType)
            append("\r\n")
            append("Content-Length: ")
            append(body.size)
            append("\r\n")
            append("Connection: close\r\n")
            extraHeaders.forEach { (name, value) ->
                append(name)
                append(": ")
                append(value)
                append("\r\n")
            }
            append("\r\n")
        }.toByteArray(StandardCharsets.ISO_8859_1)

        output.write(head)
        output.write(body)
        output.flush()
    }

    private fun isWebSocketUpgrade(request: Request): Boolean =
        request.headers["upgrade"]?.equals("websocket", ignoreCase = true) == true &&
            request.headers.containsKey("sec-websocket-key")

    private fun writeWebSocketHandshake(
        output: OutputStream,
        request: Request,
    ) {
        val key = request.headers["sec-websocket-key"] ?: return
        val digest = MessageDigest.getInstance("SHA-1").digest(
            (key + WEB_SOCKET_GUID).toByteArray(StandardCharsets.US_ASCII),
        )
        val accept = Base64.getEncoder().encodeToString(digest)
        val response = buildString {
            append("HTTP/1.1 101 Switching Protocols\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Accept: ")
            append(accept)
            append("\r\n\r\n")
        }.toByteArray(StandardCharsets.ISO_8859_1)

        output.write(response)
        output.write(byteArrayOf(0x88.toByte(), 0x00))
        output.flush()
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        runCatching { serverSocket?.close() }
        workers.shutdownNow()
        acceptThread = null
        serverSocket = null
    }

    private fun safeMarker(raw: String): String =
        raw.filter { it.isLetterOrDigit() }.take(8).ifBlank { "X" }

    companion object {
        private const val SOCKET_TIMEOUT_MS = 5_000
        private const val MAX_HEADER_BYTES = 64 * 1024
        private const val MAX_BODY_DRAIN_BYTES = 1024 * 1024
        private const val WEB_SOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

        private val EXTRA_PATH = Regex("^/extra-\\d+$")
        private val SAFE_PATH = Regex(
            "^/(?:tab-[abp]|api-json|graphql|multipart|form|redirect-[abp]|" +
                "beacon|prefetch\\.txt|preload\\.css|sw\\.js|sse|ws|bg|failed|extra-\\d+)$",
        )
    }
}

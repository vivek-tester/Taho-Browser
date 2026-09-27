package app.taho.browser.testsupport.http

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ControlledHttpFixtureServerTest {
    @Test
    fun servesLoopbackHealthResponse() {
        ControlledHttpFixtureServer().use { server ->
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI(server.url("/health"))).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals("127.0.0.1", URI(server.baseUrl).host)
            assertEquals(200, response.statusCode())
            assertEquals("""{"ok":true}""", response.body())
        }
    }

    @Test
    fun recordsSyntheticJsonPost() {
        ControlledHttpFixtureServer().use { server ->
            val payload = """{"name":"fixture"}"""
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI(server.url("/api/json?tab=A")))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(201, response.statusCode())
            val observed = server.observedRequests().single()
            assertEquals("POST", observed.method)
            assertEquals("/api/json", observed.path)
            assertEquals("tab=A", observed.query)
            assertEquals(payload, observed.bodyUtf8)
        }
    }

    @Test
    fun redirectAndFailureAreExplicit() {
        ControlledHttpFixtureServer().use { server ->
            val client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
            val redirect = client.send(
                HttpRequest.newBuilder(URI(server.url("/redirect"))).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            val failure = client.send(
                HttpRequest.newBuilder(URI(server.url("/fail"))).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(302, redirect.statusCode())
            assertEquals("/api/get", redirect.headers().firstValue("Location").orElse(""))
            assertEquals(503, failure.statusCode())
            assertTrue(failure.body().contains("failure"))
        }
    }
}

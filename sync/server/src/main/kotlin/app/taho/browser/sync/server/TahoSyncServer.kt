package app.taho.browser.sync.server

import app.taho.browser.sync.SyncDevice
import app.taho.browser.sync.SyncInputValidation
import app.taho.browser.sync.TahoSyncProtocol
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors

fun main() {
    val port = System.getenv("TAHO_SYNC_PORT")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 8787
    val bind = System.getenv("TAHO_SYNC_BIND")?.takeIf(String::isNotBlank) ?: "127.0.0.1"
    val dataFile = File(
        System.getenv("TAHO_SYNC_DATA_FILE")
            ?.takeIf(String::isNotBlank)
            ?: "data/taho-sync.bin",
    )

    val service = TahoSyncHttpService(SyncStore(dataFile))
    val server = HttpServer.create(InetSocketAddress(bind, port), 64)
    server.executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceIn(2, 16),
    )
    service.register(server)
    server.start()
}

internal class TahoSyncHttpService(
    private val store: SyncStore,
) {
    fun register(server: HttpServer) {
        server.createContext("/v1/health") { exchange ->
            respond(exchange, 200, JSONObject().put("ok", true))
        }
        server.createContext("/v1/accounts/register") { exchange ->
            route(exchange, "POST") {
                val body = readJson(exchange)
                val email = body.optString("email").trim()
                val password = body.optString("password")
                require(SyncInputValidation.validEmail(email)) { "Invalid email" }
                require(SyncInputValidation.validPassword(password)) { "Password must be 10-256 characters" }

                val chars = password.toCharArray()
                try {
                    val (account, token) = store.register(email, chars)
                    respond(
                        exchange,
                        201,
                        JSONObject()
                            .put("accountId", account.id)
                            .put("token", token)
                            .put("syncSalt", account.syncSaltBase64),
                    )
                } finally {
                    chars.fill('\u0000')
                }
            }
        }
        server.createContext("/v1/accounts/sign-in") { exchange ->
            route(exchange, "POST") {
                val body = readJson(exchange)
                val email = body.optString("email").trim()
                val password = body.optString("password")
                if (!SyncInputValidation.validEmail(email) || !SyncInputValidation.validPassword(password)) {
                    respond(exchange, 401, errorJson("Invalid credentials"))
                    return@route
                }

                val chars = password.toCharArray()
                val result = try {
                    store.signIn(email, chars)
                } finally {
                    chars.fill('\u0000')
                }
                if (result == null) {
                    respond(exchange, 401, errorJson("Invalid credentials"))
                } else {
                    val (account, token) = result
                    respond(
                        exchange,
                        200,
                        JSONObject()
                            .put("accountId", account.id)
                            .put("token", token)
                            .put("syncSalt", account.syncSaltBase64),
                    )
                }
            }
        }
        server.createContext("/v1/devices/register") { exchange ->
            routeAuthenticated(exchange, "POST") { account ->
                val body = readJson(exchange)
                val id = body.optString("id").takeIf(::validId) ?: UUID.randomUUID().toString()
                val name = body.optString("name").trim()
                val type = body.optString("type").trim().take(40)
                require(SyncInputValidation.validDeviceName(name)) { "Invalid device name" }
                val now = System.currentTimeMillis()
                val device = SyncDevice(
                    id = id,
                    name = name,
                    type = type.ifBlank { "Android" },
                    lastSeenEpochMs = now,
                )
                store.upsertDevice(account.id, device)
                respond(exchange, 200, deviceJson(device))
            }
        }
        server.createContext("/v1/devices") { exchange ->
            routeAuthenticated(exchange, "GET") { account ->
                val array = JSONArray()
                store.listDevices(account.id).forEach { array.put(deviceJson(it)) }
                respond(exchange, 200, JSONObject().put("devices", array))
            }
        }
        server.createContext("/v1/sync/state") { exchange ->
            when (exchange.requestMethod.uppercase()) {
                "GET" -> routeAuthenticated(exchange, "GET") { account ->
                    val state = store.getState(account.id)
                    if (state == null) {
                        respond(exchange, 404, errorJson("No sync state"))
                    } else {
                        respond(
                            exchange,
                            200,
                            JSONObject()
                                .put("revision", state.revision)
                                .put("updatedAt", state.updatedAtEpochMs)
                                .put("payload", state.ciphertextBase64),
                        )
                    }
                }
                "PUT" -> routeAuthenticated(exchange, "PUT") { account ->
                    val body = readJson(exchange, TahoSyncProtocol.MAX_OPAQUE_STATE_BYTES * 2)
                    val payload = body.optString("payload")
                    require(
                        SyncInputValidation.validOpaqueBase64(
                            payload,
                            TahoSyncProtocol.MAX_OPAQUE_STATE_BYTES,
                        ),
                    ) { "Invalid sync payload" }
                    val expected = if (body.has("expectedRevision") && !body.isNull("expectedRevision")) {
                        body.getLong("expectedRevision")
                    } else {
                        null
                    }
                    try {
                        val state = store.putState(
                            accountId = account.id,
                            expectedRevision = expected,
                            ciphertextBase64 = payload,
                            now = System.currentTimeMillis(),
                        )
                        respond(
                            exchange,
                            200,
                            JSONObject()
                                .put("revision", state.revision)
                                .put("updatedAt", state.updatedAtEpochMs),
                        )
                    } catch (conflict: RevisionConflict) {
                        respond(
                            exchange,
                            409,
                            errorJson("Revision conflict")
                                .put("currentRevision", conflict.currentRevision),
                        )
                    }
                }
                else -> methodNotAllowed(exchange)
            }
        }
        server.createContext("/v1/tabs/send") { exchange ->
            routeAuthenticated(exchange, "POST") { account ->
                val body = readJson(exchange, TahoSyncProtocol.MAX_SEND_TAB_BYTES * 2)
                val source = body.optString("fromDeviceId")
                val target = body.optString("targetDeviceId")
                val payload = body.optString("payload")
                require(validId(source) && validId(target)) { "Invalid device ID" }
                require(
                    SyncInputValidation.validOpaqueBase64(
                        payload,
                        TahoSyncProtocol.MAX_SEND_TAB_BYTES,
                    ),
                ) { "Invalid send-tab payload" }

                val message = store.enqueueTab(
                    accountId = account.id,
                    fromDeviceId = source,
                    targetDeviceId = target,
                    ciphertextBase64 = payload,
                    now = System.currentTimeMillis(),
                )
                respond(exchange, 202, JSONObject().put("id", message.id))
            }
        }
        server.createContext("/v1/tabs/inbox") { exchange ->
            routeAuthenticated(exchange, "GET") { account ->
                val deviceId = queryParam(exchange.requestURI.rawQuery, "deviceId").orEmpty()
                require(validId(deviceId)) { "Invalid device ID" }
                val messages = JSONArray()
                store.consumeTabs(account.id, deviceId).forEach { item ->
                    messages.put(
                        JSONObject()
                            .put("id", item.id)
                            .put("fromDeviceId", item.fromDeviceId)
                            .put("createdAt", item.createdAtEpochMs)
                            .put("payload", item.ciphertextBase64),
                    )
                }
                respond(exchange, 200, JSONObject().put("messages", messages))
            }
        }
    }

    private inline fun route(
        exchange: HttpExchange,
        method: String,
        block: () -> Unit,
    ) {
        if (!exchange.requestMethod.equals(method, ignoreCase = true)) {
            methodNotAllowed(exchange)
            return
        }
        runCatching(block).onFailure { error ->
            if (!exchange.responseHeaders.containsKey("Content-Type")) {
                val status = if (error is IllegalArgumentException) 400 else 500
                respond(exchange, status, errorJson(
                    if (status == 400) error.message ?: "Invalid request"
                    else "Internal server error",
                ))
            }
        }
    }

    private inline fun routeAuthenticated(
        exchange: HttpExchange,
        method: String,
        block: (AccountRecord) -> Unit,
    ) {
        route(exchange, method) {
            val authorization = exchange.requestHeaders.getFirst(TahoSyncProtocol.AUTH_HEADER).orEmpty()
            if (!authorization.startsWith(TahoSyncProtocol.BEARER_PREFIX)) {
                respond(exchange, 401, errorJson("Authentication required"))
                return@route
            }
            val token = authorization.removePrefix(TahoSyncProtocol.BEARER_PREFIX).trim()
            val account = token.takeIf { it.length in 32..256 }?.let(store::authenticate)
            if (account == null) {
                respond(exchange, 401, errorJson("Invalid session"))
                return@route
            }
            block(account)
        }
    }

    private fun readJson(
        exchange: HttpExchange,
        maxBytes: Int = 64 * 1024,
    ): JSONObject {
        val output = java.io.ByteArrayOutputStream()
        exchange.requestBody.use { input ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= maxBytes) { "Request body too large" }
                output.write(buffer, 0, read)
            }
        }
        return JSONObject(output.toString(StandardCharsets.UTF_8))
    }

    private fun respond(exchange: HttpExchange, status: Int, json: JSONObject) {
        val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.responseHeaders.set("X-Content-Type-Options", "nosniff")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun methodNotAllowed(exchange: HttpExchange) {
        respond(exchange, 405, errorJson("Method not allowed"))
    }

    private fun errorJson(message: String): JSONObject =
        JSONObject().put("error", message)

    private fun deviceJson(device: SyncDevice): JSONObject =
        JSONObject()
            .put("id", device.id)
            .put("name", device.name)
            .put("type", device.type)
            .put("lastSeen", device.lastSeenEpochMs)

    private fun validId(raw: String): Boolean =
        raw.length in 1..128 && raw.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }

    private fun queryParam(rawQuery: String?, name: String): String? {
        val query = rawQuery ?: return null
        return query.split('&')
            .mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.firstOrNull() != name) return@mapNotNull null
                java.net.URLDecoder.decode(
                    pieces.getOrElse(1) { "" },
                    StandardCharsets.UTF_8,
                )
            }
            .firstOrNull()
    }
}

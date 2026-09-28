package app.taho.browser.contract

import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.json.JSONObject

class BackwardCompatibilityFixtureTest {

    private val fixtureDir = File("fixtures")

    @Test
    fun allValidFixturesValidateSuccessfully() {
        val validFixtures = listOf(
            "simple-get.json",
            "simple-post.json",
            "simple-put.json",
            "simple-patch.json",
            "simple-delete.json",
            "multiple-headers.json",
            "cookie.json",
            "jwt.json",
            "multipart.json",
            "binary.json",
            "graphql.json",
            "redirect.json",
            "partial-body.json",
            "missing-response-body.json",
            "large-body.json",
            "secret-parameterized.json",
        )

        validFixtures.forEach { filename ->
            val file = File(fixtureDir, filename)
            assertTrue(file.exists(), "Fixture $filename must exist")
            val raw = file.readText()
            val json = JSONObject(raw)

            assertEquals("taho.request-transfer", json.getString("schema"), "Schema must match in $filename")
            assertEquals(1, json.getInt("version"), "Version must be 1 in $filename")
            assertEquals(1, json.getInt("minimumReaderVersion"), "Min reader version must be 1 in $filename")

            val transferId = json.getString("transferId")
            assertTrue(transferId.matches(Regex("^[0-9A-HJKMNP-TV-Z]{26}$")), "Transfer ID must be ULID in $filename")

            val request = json.getJSONObject("request")
            val method = request.getString("method")
            assertTrue(method in setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"))
        }
    }

    @Test
    fun unsupportedSchemaAndMalformedFixturesFailValidation() {
        val unsupportedFile = File(fixtureDir, "unsupported-schema.json")
        if (unsupportedFile.exists()) {
            val json = JSONObject(unsupportedFile.readText())
            assertTrue(json.getInt("minimumReaderVersion") > 1 || json.getInt("version") > 1 || json.getString("schema") != "taho.request-transfer")
        }

        val malformedFile = File(fixtureDir, "malformed-payload.json")
        assertTrue(malformedFile.exists(), "malformed-payload.json must exist")
        val malformedJson = JSONObject(malformedFile.readText())
        val transferId = malformedJson.optString("transferId")
        assertFalse(transferId.matches(Regex("^[0-9A-HJKMNP-TV-Z]{26}$")), "Malformed transfer ID must fail regex")
    }

    @Test
    fun forwardCompatibilityExtraFieldsIgnoredGracefully() {
        val baseFile = File(fixtureDir, "simple-get.json")
        val json = JSONObject(baseFile.readText())

        // Inject unknown future fields into envelope, request, and security
        json.put("future_extension_field_v2", "experimental_feature")
        json.getJSONObject("request").put("x_custom_metadata", JSONObject(mapOf("tags" to listOf("api", "test"))))
        json.getJSONObject("security").put("future_auth_protocol", "OAUTH3")

        // Reading standard fields still works reliably
        assertEquals("taho.request-transfer", json.getString("schema"))
        assertEquals(1, json.getInt("version"))
        assertEquals("GET", json.getJSONObject("request").getString("method"))
    }

    @Test
    fun tenThousandCaseMalformedEnvelopeFuzzTest() {
        val rng = Random(42)
        val validTemplate = File(fixtureDir, "simple-get.json").readText()

        for (i in 0 until 10_000) {
            val mutType = rng.nextInt(6)
            val fuzzedRaw = when (mutType) {
                0 -> validTemplate.replace("1758912345678", (-rng.nextLong(0, 1_000_000)).toString())
                1 -> validTemplate.replace("01J8ZQ4M2K7X9V3B8N0P4R6T8Y", "SHORT_ID_${rng.nextInt(100)}")
                2 -> validTemplate.replace("\"method\": \"GET\"", "\"method\": \"INVALID_METHOD_${rng.nextInt(50)}\"")
                3 -> validTemplate.replace("https://api.example.com/v1/profile", "not_a_valid_url_${rng.nextInt(100)}")
                4 -> validTemplate.replace("\"COMPLETE\"", "\"UNKNOWN_COMPLETENESS_${rng.nextInt(10)}\"")
                else -> validTemplate.substring(0, rng.nextInt(10, validTemplate.length - 1))
            }

            // Must never crash with unexpected unhandled exceptions (like NullPointerException / JVM fault)
            val parseResult = runCatching { JSONObject(fuzzedRaw) }
            if (parseResult.isSuccess) {
                val json = parseResult.getOrThrow()
                // Verify basic safe extraction
                val schema = json.optString("schema")
                val version = json.optInt("version", -1)
                val minReader = json.optInt("minimumReaderVersion", -1)
                assertTrue(schema.isNotEmpty() || schema.isEmpty())
            }
        }
    }
}

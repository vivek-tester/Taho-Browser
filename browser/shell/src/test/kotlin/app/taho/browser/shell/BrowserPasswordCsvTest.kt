package app.taho.browser.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowserPasswordCsvTest {
    @Test
    fun chromeCsvSupportsQuotedCommasAndEscapedQuotes() {
        val csv = """
            name,url,username,password,note
            "Example, Inc.","https://example.com/login","alice@example.com","pa""ss,word",""
        """.trimIndent()

        val result = BrowserPasswordCsv.parse(csv)

        assertEquals("Chrome", result.format)
        assertEquals(1, result.records.size)
        assertEquals("https://example.com/login", result.records.single().url)
        assertEquals("alice@example.com", result.records.single().username)
        assertEquals("pa\"ss,word", result.records.single().password)
        assertEquals(0, result.rejectedRows)
    }

    @Test
    fun firefoxCsvUsesUrlUsernamePasswordColumns() {
        val csv = """
            url,username,password,httpRealm,formActionOrigin,guid,timeCreated,timeLastUsed,timePasswordChanged
            https://mozilla.example,user1,secret1,,https://mozilla.example/login,{guid},1,2,3
        """.trimIndent()

        val result = BrowserPasswordCsv.parse(csv)

        assertEquals("Firefox", result.format)
        assertEquals(1, result.records.size)
        assertEquals("https://mozilla.example", result.records.single().url)
    }

    @Test
    fun invalidRowsAreRejectedInsteadOfInvented() {
        val csv = """
            url,username,password
            javascript:alert(1),u,p
            https://good.example,u2,
        """.trimIndent()

        val result = BrowserPasswordCsv.parse(csv)

        assertTrue(result.records.isEmpty())
        assertEquals(2, result.rejectedRows)
    }
}

package app.taho.browser.transfer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UlidGeneratorTest {
    @Test
    fun generatedIdsMatchContractShape() {
        val id = UlidGenerator.next(123456789L)
        assertEquals(26, id.length)
        assertTrue(Regex("^[0-9A-HJKMNP-TV-Z]{26}$").matches(id))
    }

    @Test
    fun randomComponentPreventsSameTimestampCollisionInOrdinaryUse() {
        assertNotEquals(
            UlidGenerator.next(123456789L),
            UlidGenerator.next(123456789L),
        )
    }
}

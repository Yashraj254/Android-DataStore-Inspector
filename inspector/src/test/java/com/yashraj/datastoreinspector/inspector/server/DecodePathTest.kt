package com.yashraj.datastoreinspector.inspector.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DecodePathTest {

    @Test
    fun decodesPercentEncodedSpaces() {
        assertEquals("/api/sharedprefs/my prefs", decodePath("/api/sharedprefs/my%20prefs"))
    }

    @Test
    fun keepsPlusLiteral() {
        // '+' means space only in form bodies, not in URL paths.
        assertEquals("/api/proto/a+b", decodePath("/api/proto/a+b"))
    }

    @Test
    fun decodesReservedAndUnicodeCharacters() {
        assertEquals("/api/datastore/a#b?c", decodePath("/api/datastore/a%23b%3Fc"))
        assertEquals("/api/datastore/हिंदी", decodePath("/api/datastore/%E0%A4%B9%E0%A4%BF%E0%A4%82%E0%A4%A6%E0%A5%80"))
    }

    @Test
    fun rejectsMalformedEscapes() {
        assertThrows(IllegalArgumentException::class.java) { decodePath("/api/proto/%zz") }
    }
}

package io.github.azx8788a.pan123.service

import org.junit.Assert.*
import org.junit.Test

class RapidDataTest {
    private val hash = "900150983cd24fb0d6963f7d28e17f72"

    @Test fun jsonRoundTripPreservesUnicodePaths() {
        val files = listOf(RapidFile("照片/测试.txt", hash, 3))
        assertEquals(files, RapidData.parse(RapidData.export(files)))
    }

    @Test fun parsesFastLinkAndLegacyFormat() {
        assertEquals("folder/test.txt", RapidData.parse("123FLCPV2\$folder/%$hash#3#test.txt").single().path)
        assertEquals(3L, RapidData.parse("$hash#3#test.txt").single().size)
    }

    @Test fun rejectsInputWithoutValidMd5() {
        try { RapidData.parse("invalid#3#test.txt"); fail("Expected parse failure") } catch (_: IllegalArgumentException) { }
    }
}

package com.hoisly.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BackupCodecTest {
    @Test
    fun everyTypeSurvivesARoundTrip() {
        val settings = mapOf<String, Any?>(
            "flag" to true, "count" to 7, "stamp" to 1234567890123L, "ratio" to 0.75f,
            "name" to "Hoisly", "set" to setOf("a", "b"),
        )
        val backup = BackupCodec.decode(BackupCodec.encode(settings, "{\"grid\":4}", 1L))
        assertNotNull(backup)
        assertEquals(settings, backup!!.settings)
        assertEquals("{\"grid\":4}", backup.layout)
    }

    @Test
    fun layoutIsOptional() {
        val backup = BackupCodec.decode(BackupCodec.encode(mapOf("a" to 1), null, 0L))
        assertNull(backup!!.layout)
    }

    @Test
    fun otherFilesAreRejected() {
        assertNull(BackupCodec.decode("not json"))
        assertNull(BackupCodec.decode("{\"app\":\"other\",\"format\":1,\"settings\":{}}"))
        assertNull(BackupCodec.decode("{\"app\":\"hoisly-backup\",\"format\":99,\"settings\":{}}"))
    }

    @Test
    fun unsupportedValuesAreSkippedWhenWriting() {
        val backup = BackupCodec.decode(BackupCodec.encode(mapOf("ok" to 1, "bad" to Any()), null, 0L))
        assertEquals(setOf("ok"), backup!!.settings.keys)
    }
}

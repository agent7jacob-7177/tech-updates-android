package com.code7.techupdates

import org.junit.Assert.assertEquals
import org.junit.Test

class AppInfoTest {
    @Test
    fun versionLabel_formatsNameAndCode() {
        assertEquals("v0.1.0 (1)", versionLabel("0.1.0", 1))
    }

    @Test
    fun buildConfig_matchesExpectedVersion() {
        assertEquals("0.1.0", BuildConfig.VERSION_NAME)
        assertEquals(1, BuildConfig.VERSION_CODE)
    }
}

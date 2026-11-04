package com.aritxonly.myhypermodifier

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PiliPlusThemeSettingTest {
    @Test
    fun readsTheLatestHiveThemeModeFrame() {
        val bytes = ByteArrayOutputStream().apply {
            write(themeModeFrame(0))
            write(themeModeFrame(1))
        }.toByteArray()

        assertEquals(1, PiliPlusThemeSetting.themeModeFromHive(bytes))
    }

    @Test
    fun ignoresMissingOrInvalidThemeModeValues() {
        assertNull(PiliPlusThemeSetting.themeModeFromHive("themeMode".toByteArray()))
        assertNull(PiliPlusThemeSetting.themeModeFromHive(themeModeFrame(9)))
    }

    @Test
    fun deletionOfTheLatestValueDoesNotRestoreAnOlderOne() {
        val bytes = ByteArrayOutputStream().apply {
            write(themeModeFrame(1))
            write(1)
            write("themeMode".length)
            write("themeMode".toByteArray())
            write(ByteArray(10))
        }.toByteArray()

        assertNull(PiliPlusThemeSetting.themeModeFromHive(bytes))
    }

    private fun themeModeFrame(value: Int): ByteArray {
        val key = "themeMode".toByteArray()
        return ByteArrayOutputStream().apply {
            write(1)
            write(key.size)
            write(key)
            write(1)
            write(
                ByteBuffer.allocate(8)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putDouble(value.toDouble())
                    .array(),
            )
        }.toByteArray()
    }
}

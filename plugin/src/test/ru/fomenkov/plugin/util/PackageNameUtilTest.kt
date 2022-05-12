package ru.fomenkov.plugin.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PackageNameUtilTest {

    @Test
    fun test() {
        assertEquals(
            listOf("com", "android", "framework"),
            PackageNameUtil.split(packageName = "com.android.framework"),
        )
        assertEquals(
            listOf("com", "android"),
            PackageNameUtil.split(packageName = "com.android.framework", ignoreLast = 1),
        )
        assertEquals(
            listOf("com", "android", "framework"),
            PackageNameUtil.split(packageName = "com.android.framework.*", ignoreLast = 1),
        )
        assertEquals(
            listOf("com", "android"),
            PackageNameUtil.split(packageName = "com.android.framework.*", ignoreLast = 2),
        )
        assertEquals(
            emptyList(),
            PackageNameUtil.split(packageName = ""),
        )
    }
}
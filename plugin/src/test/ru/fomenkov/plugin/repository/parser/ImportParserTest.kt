package ru.fomenkov.plugin.repository.parser

import org.junit.jupiter.api.Test
import ru.fomenkov.plugin.repository.data.Import
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportParserTest {

    @Test
    fun testCommonCases() {
        val parser = ImportParser()

        assertNull(parser.parse("package com.android.framework;"))
        assertNull(parser.parse(""))
        assertNull(parser.parse("//import com.android.framework"))
        assertNull(parser.parse(" // import com.android.framework"))
        assertNull(parser.parse("public static void main"))
        assertEquals(
            Import(packageName = "com.android.framework", isStatic = false, hasTrailingWildcard = false),
            parser.parse("import com.android.framework"),
        )
        assertEquals(
            Import(packageName = "com.android.framework", isStatic = false, hasTrailingWildcard = false),
            parser.parse("import com.android.framework;"),
        )
        assertEquals(
            Import(packageName = "com.android.framework", isStatic = true, hasTrailingWildcard = false),
            parser.parse("import static com.android.framework;"),
        )
        assertEquals(
            Import(packageName = "com.android.framework", isStatic = true, hasTrailingWildcard = true),
            parser.parse("import static com.android.framework.*;"),
        )
        assertEquals(
            Import(packageName = "com.android.framework", isStatic = false, hasTrailingWildcard = true),
            parser.parse("import com.android.framework.*;"),
        )
        assertEquals(
            Import(packageName = "com.android.framework", isStatic = false, hasTrailingWildcard = true),
            parser.parse("import com.android.framework.*;"),
        )
    }

    @Test
    fun testEdgeCases() {
        val parser = ImportParser()

        assertEquals(
            Import(packageName = "com.android.framework", isStatic = false, hasTrailingWildcard = false),
            parser.parse("import    com.android.framework"),
        )
        assertEquals(
            Import(packageName = "com.android.framework", isStatic = false, hasTrailingWildcard = false),
            parser.parse("  import  com.android.framework;  "),
        )
        assertEquals(
            Import(packageName = "com.android.framework", isStatic = true, hasTrailingWildcard = false),
            parser.parse("import  static  com.android.framework  ;"),
        )
        assertEquals(
            Import(packageName = "com.android.framework", isStatic = true, hasTrailingWildcard = true),
            parser.parse("import static com.android.framework.*; // import"),
        )
        assertEquals(
            Import(packageName = "com.android.framework", isStatic = false, hasTrailingWildcard = true),
            parser.parse("import com.android.framework.*;//import"),
        )
        assertEquals(
            Import(packageName = "com.android.framework", isStatic = false, hasTrailingWildcard = false),
            parser.parse(" import    com.android.framework//import"),
        )
        assertEquals(
            Import(packageName = "com.android.framework", isStatic = false, hasTrailingWildcard = false),
            parser.parse("  import com.android.framework   // import"),
        )
        assertEquals(
            Import(packageName = "com.android.framework", isStatic = false, hasTrailingWildcard = false),
            parser.parse("  import com.android.framework   //; import"),
        )
    }

    @Test
    fun testImportParts() {
        assertEquals(
            listOf("com", "android", "framework"),
            Import(packageName = "com.android.framework", isStatic = false, hasTrailingWildcard = false).parts(),
        )
    }
}
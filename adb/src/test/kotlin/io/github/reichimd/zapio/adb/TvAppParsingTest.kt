package io.github.reichimd.zapio.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TvAppParsingTest {

    @Test
    fun readsPackagesWithEqualsSignsInThePath() {
        val output = """
            package:/data/app/~~a1B2==/com.example.one-Zx9==/base.apk=com.example.one
            package:/data/app/com.example.two-1/base.apk=com.example.two
            something else
            package:/broken
        """.trimIndent()
        assertEquals(
            listOf(
                "com.example.one" to "/data/app/~~a1B2==/com.example.one-Zx9==/base.apk",
                "com.example.two" to "/data/app/com.example.two-1/base.apk",
            ),
            TvAppParsing.parsePackages(output),
        )
    }

    @Test
    fun readsSizes() {
        val sizes = TvAppParsing.parseSizes("1234 /data/app/x/base.apk\nnope\n99 /data/app/y y/base.apk\n")
        assertEquals(mapOf("/data/app/x/base.apk" to 1234L, "/data/app/y y/base.apk" to 99L), sizes)
    }

    @Test
    fun readsDetails() {
        val output = """
                versionCode=42 minSdk=21 targetSdk=34
                versionName=1.2.3 beta
                firstInstallTime=2026-10-01 12:30:00
                lastUpdateTime=2026-10-29 08:00:01
                versionName=old
        """.trimIndent()
        assertEquals(
            TvAppDetails("1.2.3 beta", "42", "2026-10-01 12:30:00", "2026-10-29 08:00:01"),
            TvAppParsing.parseDetails(output),
        )
        assertEquals(TvAppDetails(null, null, null, null), TvAppParsing.parseDetails(""))
    }

    @Test
    fun onlyRealPackageNamesReachTheShell() {
        assertEquals("com.example.tv.b8", TvAppParsing.checked("com.example.tv.b8"))
        for (bad in listOf("", "com", "com.x; reboot", "com.x\nreboot", "-k com.x", "com..x", "\$(id).x")) {
            assertThrows(IllegalArgumentException::class.java) { TvAppParsing.checked(bad) }
        }
    }

    @Test
    fun pmResult() {
        assertEquals(TvResult.Success, TvAppParsing.successOrReason(ShellResult("Success\n", "", 0)))
        val failure = TvAppParsing.successOrReason(ShellResult("Failure [DELETE_FAILED_INTERNAL_ERROR]\n", "", 1))
        assertTrue(failure is TvResult.Failure)
        assertEquals("Failure [DELETE_FAILED_INTERNAL_ERROR]", (failure as TvResult.Failure).reason)
    }
}

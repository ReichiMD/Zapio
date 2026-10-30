package io.github.reichimd.zapio.adb

import io.github.reichimd.zapio.adb.TvPaths.quote

/** An app the user installed on the TV; [apkBytes] is the size of its main APK, null if unknown. */
data class TvApp(val packageName: String, val apkPath: String, val apkBytes: Long?)

/** What the TV knows about one app. Fields the TV does not report stay null. */
data class TvAppDetails(
    val versionName: String?,
    val versionCode: String?,
    val firstInstall: String?,
    val lastUpdate: String?,
)

/*
 * Apps on the TV through the package manager. Only apps the user installed are listed:
 * removing parts of the system could break the TV.
 */

/** The user's apps, sorted by package name. */
fun TvConnection.listApps(): List<TvApp> {
    val listed = TvAppParsing.parsePackages(shellText("pm list packages -3 -f"))
    if (listed.isEmpty()) return emptyList()
    // Few calls for all sizes, in batches so a command never gets too long; a path the shell cannot read stays without size.
    val sizes = listed.chunked(STAT_BATCH).flatMap { batch ->
        val paths = batch.joinToString(" ") { quote(it.second) }
        TvAppParsing.parseSizes(shellText("stat -c '%s %n' -- $paths 2>/dev/null")).toList()
    }.toMap()
    return listed
        .map { (name, path) -> TvApp(name, path, sizes[path]) }
        .sortedBy { it.packageName.lowercase() }
}

fun TvConnection.appDetails(packageName: String): TvAppDetails {
    val name = TvAppParsing.checked(packageName)
    val text = shellText("dumpsys package $name | grep -E 'versionName=|versionCode=|firstInstallTime=|lastUpdateTime='")
    return TvAppParsing.parseDetails(text)
}

/** Removes the app with all its data. */
fun TvConnection.uninstall(packageName: String): TvResult =
    TvAppParsing.successOrReason(shell("pm uninstall ${TvAppParsing.checked(packageName)}"))

/** Deletes the app's data, as if freshly installed. */
fun TvConnection.clearData(packageName: String): TvResult =
    TvAppParsing.successOrReason(shell("pm clear ${TvAppParsing.checked(packageName)}"))

fun TvConnection.forceStop(packageName: String): TvResult =
    shell("am force-stop ${TvAppParsing.checked(packageName)}").toTvResult()

/**
 * Starts the app. TV apps have a TV launcher entry; phone apps on the TV only a normal one,
 * so that one is tried second.
 */
fun TvConnection.openApp(packageName: String): TvResult {
    val name = TvAppParsing.checked(packageName)
    val tv = shell("monkey -p $name -c android.intent.category.LEANBACK_LAUNCHER 1")
    if (TvAppParsing.monkeyStarted(tv)) return TvResult.Success
    val phone = shell("monkey -p $name -c android.intent.category.LAUNCHER 1")
    if (TvAppParsing.monkeyStarted(phone)) return TvResult.Success
    return TvResult.Failure(phone.output.ifBlank { phone.errorOutput }.trim())
}

/** Reading what the package manager prints. Kept apart so it can be tested without a TV. */
object TvAppParsing {

    private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")

    /** [name] if it is a real package name. Anything else never reaches the shell. */
    fun checked(name: String): String {
        require(PACKAGE_NAME.matches(name)) { "not a package name" }
        return name
    }

    /**
     * `package:/data/app/…/base.apk=com.example` → (`com.example`, path). The path may itself
     * contain `=`, so the name is what follows the last one.
     */
    fun parsePackages(output: String): List<Pair<String, String>> = output.lines().mapNotNull { line ->
        val text = line.trim().removePrefix("package:")
        if (text == line.trim()) return@mapNotNull null
        val cut = text.lastIndexOf('=')
        if (cut <= 0) return@mapNotNull null
        val name = text.substring(cut + 1)
        if (!PACKAGE_NAME.matches(name)) return@mapNotNull null
        name to text.substring(0, cut)
    }

    /** `size path` per line → sizes by path. */
    fun parseSizes(output: String): Map<String, Long> = output.lines().mapNotNull { line ->
        val parts = line.trim().split(' ', limit = 2)
        if (parts.size < 2) return@mapNotNull null
        val size = parts[0].toLongOrNull() ?: return@mapNotNull null
        parts[1] to size
    }.toMap()

    /** Reads the first of each value; `versionCode=12 minSdk=21 …` keeps only the number. */
    fun parseDetails(output: String): TvAppDetails {
        fun value(key: String): String? = output.lines().firstNotNullOfOrNull { line ->
            val at = line.indexOf("$key=")
            if (at < 0) return@firstNotNullOfOrNull null
            val rest = line.substring(at + key.length + 1).trim()
            // `versionCode=12 minSdk=21 …` holds more values; dates and version names may contain spaces.
            val value = if (key == "versionCode") rest.substringBefore(' ') else rest
            value.ifEmpty { null }
        }
        return TvAppDetails(value("versionName"), value("versionCode"), value("firstInstallTime"), value("lastUpdateTime"))
    }

    /** `pm` prints `Success`, or `Failure [REASON]` and sometimes an exception. */
    internal fun successOrReason(result: ShellResult): TvResult {
        val text = (result.output + "\n" + result.errorOutput).trim()
        return if (text.lines().any { it.trim() == "Success" }) TvResult.Success else TvResult.Failure(text)
    }

    /** monkey says "Events injected: 1" once it started something. */
    internal fun monkeyStarted(result: ShellResult): Boolean =
        (result.output + result.errorOutput).contains("Events injected: 1")
}

private const val STAT_BATCH = 100

private fun ShellResult.toTvResult(): TvResult =
    if (isSuccess) TvResult.Success else TvResult.Failure(errorOutput.ifBlank { output }.trim())

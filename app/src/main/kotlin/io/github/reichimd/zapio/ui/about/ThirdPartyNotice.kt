package io.github.reichimd.zapio.ui.about

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import io.github.reichimd.zapio.R

/**
 * Third-party code and fonts shipped in the app. Every new library needs an entry
 * here; the Apache License requires its notice in the app.
 */
enum class ThirdPartyNotice(
    @StringRes val title: Int,
    @StringRes val author: Int,
    @StringRes val licenseName: Int,
    @RawRes val licenseText: Int,
) {
    DADB(R.string.notice_dadb_name, R.string.notice_dadb_author, R.string.license_apache_2_0, R.raw.license_apache_2_0),
    OKIO(R.string.notice_okio_name, R.string.notice_okio_author, R.string.license_apache_2_0, R.raw.license_apache_2_0),
    ANDROIDX(R.string.notice_androidx_name, R.string.notice_androidx_author, R.string.license_apache_2_0, R.raw.license_apache_2_0),
    KOTLIN(R.string.notice_kotlin_name, R.string.notice_kotlin_author, R.string.license_apache_2_0, R.raw.license_apache_2_0),
    SPACE_GROTESK(
        R.string.notice_space_grotesk_name,
        R.string.notice_space_grotesk_author,
        R.string.license_ofl_1_1,
        R.raw.license_ofl_1_1,
    ),
    ;

    val id: String get() = name

    companion object {
        fun byId(id: String): ThirdPartyNotice? = entries.firstOrNull { it.name == id }
    }
}

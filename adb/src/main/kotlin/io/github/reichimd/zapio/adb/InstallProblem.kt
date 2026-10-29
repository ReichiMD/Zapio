package io.github.reichimd.zapio.adb

/**
 * Sorts the package manager's failure text into cases the app can explain.
 * The TV answers with codes like `Failure [INSTALL_FAILED_VERSION_DOWNGRADE: ...]`.
 */
enum class InstallProblem {
    NOT_AN_APK,
    NO_SPACE,
    SIGNATURE_MISMATCH,
    OLDER_VERSION,
    TV_TOO_OLD,
    APP_TOO_OLD,
    WRONG_PROCESSOR,
    BLOCKED,
    OTHER,
    ;

    companion object {
        // Checked in order: the certificate code also starts with INSTALL_PARSE_FAILED.
        private val CODES = listOf(
            "INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES" to SIGNATURE_MISMATCH,
            "INSTALL_FAILED_UPDATE_INCOMPATIBLE" to SIGNATURE_MISMATCH,
            "INSTALL_FAILED_SHARED_USER_INCOMPATIBLE" to SIGNATURE_MISMATCH,
            "INSTALL_PARSE_FAILED" to NOT_AN_APK,
            "INSTALL_FAILED_INVALID_APK" to NOT_AN_APK,
            "INSTALL_FAILED_INSUFFICIENT_STORAGE" to NO_SPACE,
            "INSTALL_FAILED_VERSION_DOWNGRADE" to OLDER_VERSION,
            "INSTALL_FAILED_OLDER_SDK" to TV_TOO_OLD,
            "INSTALL_FAILED_DEPRECATED_SDK_VERSION" to APP_TOO_OLD,
            "INSTALL_FAILED_NO_MATCHING_ABIS" to WRONG_PROCESSOR,
            "INSTALL_FAILED_CPU_ABI_INCOMPATIBLE" to WRONG_PROCESSOR,
            "INSTALL_FAILED_USER_RESTRICTED" to BLOCKED,
            "INSTALL_FAILED_VERIFICATION_FAILURE" to BLOCKED,
            "INSTALL_FAILED_VERIFICATION_TIMEOUT" to BLOCKED,
            "INSTALL_FAILED_ABORTED" to BLOCKED,
        )

        fun of(reason: String): InstallProblem =
            CODES.firstOrNull { (code, _) -> reason.contains(code) }?.second ?: OTHER
    }
}

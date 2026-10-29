package io.github.reichimd.zapio.adb

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallProblemTest {

    @Test
    fun knownCodes() {
        mapOf(
            "Failure [INSTALL_PARSE_FAILED_NOT_APK: Failed to parse /data/app/vmdl.tmp/base.apk]" to InstallProblem.NOT_AN_APK,
            "Failure [INSTALL_FAILED_INVALID_APK: Failed to extract native libraries]" to InstallProblem.NOT_AN_APK,
            "Failure [INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES]" to InstallProblem.SIGNATURE_MISMATCH,
            "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package x signatures do not match]" to InstallProblem.SIGNATURE_MISMATCH,
            "Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]" to InstallProblem.NO_SPACE,
            "Failure [INSTALL_FAILED_VERSION_DOWNGRADE]" to InstallProblem.OLDER_VERSION,
            "Failure [INSTALL_FAILED_OLDER_SDK: Requires newer sdk version #34 (current version is #28)]" to InstallProblem.TV_TOO_OLD,
            "Failure [INSTALL_FAILED_DEPRECATED_SDK_VERSION: App package must target at least SDK version 24]" to InstallProblem.APP_TOO_OLD,
            "Failure [INSTALL_FAILED_NO_MATCHING_ABIS: Failed to extract native libraries, res=-113]" to InstallProblem.WRONG_PROCESSOR,
            "Failure [INSTALL_FAILED_USER_RESTRICTED: Install canceled by user]" to InstallProblem.BLOCKED,
            "Failure [something new]" to InstallProblem.OTHER,
            "" to InstallProblem.OTHER,
        ).forEach { (reason, expected) -> assertEquals(reason, expected, InstallProblem.of(reason)) }
    }
}

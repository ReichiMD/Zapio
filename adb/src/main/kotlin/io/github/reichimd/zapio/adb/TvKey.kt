package io.github.reichimd.zapio.adb

/** Remote control keys, with their Android key codes. */
enum class TvKey(val code: Int) {
    HOME(3),
    BACK(4),
    DPAD_UP(19),
    DPAD_DOWN(20),
    DPAD_LEFT(21),
    DPAD_RIGHT(22),
    DPAD_CENTER(23),
    VOLUME_UP(24),
    VOLUME_DOWN(25),
    POWER(26),
    MENU(82),
    VOLUME_MUTE(164),
}

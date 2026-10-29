package io.github.reichimd.zapio.adb

import java.io.IOException

/** Why talking to a TV did not work, in terms the app can explain. */
enum class TvProblem {
    /** The TV answers, but ADB debugging is off. */
    ADB_OFF,

    /** No answer: TV off, asleep, wrong address or another network. */
    NO_ANSWER,

    /** The TV did not allow this phone (prompt declined or not answered in time). */
    NOT_ALLOWED,

    /** The connection broke while working. */
    CONNECTION_LOST,
}

class TvException(val problem: TvProblem, cause: Throwable? = null) :
    IOException("TV problem: $problem", cause)

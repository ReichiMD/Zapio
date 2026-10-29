package io.github.reichimd.zapio

import android.app.Application
import io.github.reichimd.zapio.data.DeviceStore
import io.github.reichimd.zapio.data.TargetFolder
import io.github.reichimd.zapio.tv.TvManager

/** Holds the parts that live as long as the app: saved TVs, the target folder and the TV connection. */
class ZapioApplication : Application() {
    val devices by lazy { DeviceStore(this) }
    val targetFolder by lazy { TargetFolder(this) }
    val tvs by lazy { TvManager(this, devices, targetFolder) }
}

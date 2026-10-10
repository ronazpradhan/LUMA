package com.stash.core.data.audio

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Is the device on external power right now?
 *
 * Sampled at decision time rather than observed — same shape and reasoning
 * as `ConnectivityMonitor`: the callers are one-shot "should I do this
 * expensive thing now?" checks, so a Flow would be machinery without a
 * reader.
 *
 * Reads `EXTRA_PLUGGED` from the `ACTION_BATTERY_CHANGED` sticky broadcast,
 * deliberately NOT `BatteryManager.isCharging`. That API reads BatteryStats'
 * hysteresis "considered charging" flag, which was observed wedged FALSE for
 * over an hour on a genuinely charging Pixel 6 Pro (status=charging,
 * plug=ac, JobScheduler's CHARGING constraint satisfied the whole time) —
 * it silently turned "measure loudness now, power is free" into "always
 * defer to the overnight worker". Plugged state is the semantic we actually
 * want (the CPU costs nothing extra), and it is the same signal JobScheduler
 * keys its own power tracking off, so the download-time gate and the
 * backfill worker's charging constraint can never disagree for long.
 *
 * Open so tests can substitute an answer without a Robolectric context.
 */
@Singleton
open class ChargingMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    open fun isCharging(): Boolean = runCatching {
        val battery = context.registerReceiver(
            /* receiver = */ null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        plugged != 0
    }.getOrDefault(false)
}

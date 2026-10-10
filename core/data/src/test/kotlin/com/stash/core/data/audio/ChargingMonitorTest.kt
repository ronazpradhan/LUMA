package com.stash.core.data.audio

import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins WHERE [ChargingMonitor] reads power state from: the
 * `ACTION_BATTERY_CHANGED` sticky broadcast's `EXTRA_PLUGGED`, not
 * `BatteryManager.isCharging`.
 *
 * Device evidence (Pixel 6 Pro, Android 16, 2026-08-05): a genuinely
 * charging phone — `dumpsys battery` status=charging plug=ac, and
 * JobScheduler's CHARGING constraint satisfied for over an hour — kept
 * returning `isCharging() == false` from `BatteryManager.isCharging`,
 * because that API reads BatteryStats' hysteresis "considered charging"
 * flag, which can lag or wedge. Every download deferred its loudness
 * measurement to the overnight worker even ON POWER, killing the
 * measure-immediately path the deferral fix intended to keep. Plugged
 * state is the signal that matches the semantic we want ("the CPU is
 * free"), and it is what JobScheduler's own power tracking keys off.
 */
@RunWith(RobolectricTestRunner::class)
class ChargingMonitorTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun stickBatteryBroadcast(plugged: Int) {
        context.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED, plugged),
        )
    }

    @Test
    fun `plugged into AC reads as charging`() {
        stickBatteryBroadcast(BatteryManager.BATTERY_PLUGGED_AC)

        assertThat(ChargingMonitor(context).isCharging()).isTrue()
    }

    @Test
    fun `plugged into USB reads as charging`() {
        stickBatteryBroadcast(BatteryManager.BATTERY_PLUGGED_USB)

        assertThat(ChargingMonitor(context).isCharging()).isTrue()
    }

    @Test
    fun `unplugged reads as not charging`() {
        stickBatteryBroadcast(0)

        assertThat(ChargingMonitor(context).isCharging()).isFalse()
    }

    @Test
    fun `no sticky broadcast at all reads as not charging`() {
        // Defensive default: with nothing to read, assume battery so the
        // expensive path is skipped and the backfill worker picks it up.
        assertThat(ChargingMonitor(context).isCharging()).isFalse()
    }
}

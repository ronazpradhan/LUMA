package com.stash.core.media.streaming

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

/**
 * Pure-JVM tests for [ConnectivityMonitor]. We don't need Robolectric
 * here — the monitor only consults its injected [Context]'s system
 * service, so mocking [Context] + [ConnectivityManager] + the network
 * capabilities object covers every code path.
 */
class ConnectivityMonitorTest {

    private val cm: ConnectivityManager = mockk()
    private val context: Context = mockk {
        every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns cm
    }
    private val monitor = ConnectivityMonitor(context)

    @Test
    fun isConnected_returnsTrueOnValidatedInternet() {
        val network: Network = mockk()
        val caps: NetworkCapabilities = mockk {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        }
        every { cm.activeNetwork } returns network
        every { cm.getNetworkCapabilities(network) } returns caps

        assertThat(monitor.isConnected()).isTrue()
    }

    @Test
    fun isConnected_returnsFalseWhenNoActiveNetwork() {
        every { cm.activeNetwork } returns null
        every { cm.getNetworkCapabilities(null) } returns null

        assertThat(monitor.isConnected()).isFalse()
    }

    @Test
    fun isConnected_returnsFalseWhenInternetButNotValidated() {
        // Captive-portal landing page: associated, internet-bearing in
        // principle, but NET_CAPABILITY_VALIDATED still pending.
        val network: Network = mockk()
        val caps: NetworkCapabilities = mockk {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false
        }
        every { cm.activeNetwork } returns network
        every { cm.getNetworkCapabilities(network) } returns caps

        assertThat(monitor.isConnected()).isFalse()
    }

    @Test
    fun isCellular_returnsTrueOnCellularTransport() {
        val network: Network = mockk()
        val caps: NetworkCapabilities = mockk {
            every { hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        }
        every { cm.activeNetwork } returns network
        every { cm.getNetworkCapabilities(network) } returns caps

        assertThat(monitor.isCellular()).isTrue()
    }

    @Test
    fun isCellular_returnsFalseOnWifi() {
        val network: Network = mockk()
        val caps: NetworkCapabilities = mockk {
            every { hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        }
        every { cm.activeNetwork } returns network
        every { cm.getNetworkCapabilities(network) } returns caps

        assertThat(monitor.isCellular()).isFalse()
    }

    @Test
    fun isCellular_returnsFalseWhenNoActiveNetwork() {
        every { cm.activeNetwork } returns null
        every { cm.getNetworkCapabilities(null) } returns null

        assertThat(monitor.isCellular()).isFalse()
    }
}

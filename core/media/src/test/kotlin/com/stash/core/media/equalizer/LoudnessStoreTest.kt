// LoudnessStoreTest.kt
package com.stash.core.media.equalizer

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LoudnessStoreTest {
  private lateinit var store: LoudnessStore
  private lateinit var file: File

  @Before fun setUp() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    file = ctx.preferencesDataStoreFile("loudness_state_test")
    val ds = PreferenceDataStoreFactory.create { file }
    store = LoudnessStore(ds)
  }

  @After fun tearDown() { file.delete() }

  @Test fun `read on missing key returns default with enabled true and target -14`() = runBlocking {
    val s = store.read()
    assertThat(s.enabled).isTrue()
    assertThat(s.targetLufs).isEqualTo(-14f)
    assertThat(s).isEqualTo(LoudnessState())
  }

  @Test fun `write then read round-trip`() = runBlocking {
    val original = LoudnessState(enabled = false, targetLufs = -11f)
    store.write(original)
    val restored = store.read()
    assertThat(restored).isEqualTo(original)
  }

  @Test fun `corrupted JSON falls back to default`() = runBlocking {
    store.writeRaw("{bad}")
    val s = store.read()
    assertThat(s).isEqualTo(LoudnessState())
  }
}

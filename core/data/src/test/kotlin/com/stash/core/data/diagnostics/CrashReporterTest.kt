package com.stash.core.data.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Unit tests for [CrashReporter].
 *
 * The handler does two things: write the crash to a file via
 * [CrashFileStore], and chain to the previous default handler. We can't
 * actually let the JVM-level handler run (it would kill the test JVM),
 * so we capture & replace [Thread.getDefaultUncaughtExceptionHandler]
 * around each test and trigger the exception on a dedicated worker
 * thread whose own handler we also wire up — that way the test process
 * survives and we can assert both that the file appeared on disk and
 * that the previous handler was invoked exactly once.
 */
@RunWith(RobolectricTestRunner::class)
class CrashReporterTest {

    private lateinit var context: Context
    private lateinit var store: CrashFileStore
    private lateinit var reporter: CrashReporter
    private lateinit var crashDir: File
    private var savedDefault: Thread.UncaughtExceptionHandler? = null

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = CrashFileStore(context)
        reporter = CrashReporter(context, store)
        crashDir = File(context.cacheDir, "crashes")
        crashDir.deleteRecursively()
        savedDefault = Thread.getDefaultUncaughtExceptionHandler()
    }

    @After fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(savedDefault)
        crashDir.deleteRecursively()
    }

    @Test fun `install writes the crash and delegates to the previous handler`() {
        val delegated = AtomicReference<Throwable?>(null)
        Thread.setDefaultUncaughtExceptionHandler { _, t -> delegated.set(t) }

        reporter.install()

        val boom = RuntimeException("install-test-marker")
        // Trigger via a dedicated thread that swallows its own crash so
        // the test JVM doesn't terminate. Java will invoke the global
        // default uncaught handler (which the reporter just hooked) only
        // when no per-thread handler claims it — so set ours.
        val latch = CountDownLatch(1)
        val worker = Thread {
            try {
                throw boom
            } catch (t: Throwable) {
                // Manually call the default handler the way the JVM would.
                Thread.getDefaultUncaughtExceptionHandler()
                    ?.uncaughtException(Thread.currentThread(), t)
                latch.countDown()
            }
        }
        worker.start()
        assertTrue("worker did not complete", latch.await(2, TimeUnit.SECONDS))

        // File should be on disk and contain our marker.
        val latest = store.latestCrashFile()
        assertNotNull("expected a crash file to be written", latest)
        assertTrue(latest!!.readText().contains("install-test-marker"))

        // Previous handler should have been called with the same throwable.
        assertSame(boom, delegated.get())
    }

    @Test fun `install is idempotent`() {
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> /* noop */ }
        reporter.install()
        val first = Thread.getDefaultUncaughtExceptionHandler()
        reporter.install()
        val second = Thread.getDefaultUncaughtExceptionHandler()
        assertSame("install() should not stack handlers", first, second)
    }

    @Test fun `chains to previous handler even when the writer throws`() {
        val delegated = AtomicReference<Throwable?>(null)
        Thread.setDefaultUncaughtExceptionHandler { _, t -> delegated.set(t) }

        val throwingStore = object : CrashFileStore(context) {
            override fun writeCrash(thread: Thread, throwable: Throwable) {
                throw IllegalStateException("writer is sad")
            }
        }
        val r = CrashReporter(context, throwingStore)
        r.install()

        val boom = RuntimeException("chain-after-writer-fail")
        Thread.getDefaultUncaughtExceptionHandler()
            ?.uncaughtException(Thread.currentThread(), boom)

        assertSame("previous handler must still be called", boom, delegated.get())
    }

    @Test fun `tolerates a null previous handler`() {
        // Pre-install: no default handler. The reporter should still write
        // the file and not blow up when it tries to chain through nothing.
        Thread.setDefaultUncaughtExceptionHandler(null)
        reporter.install()

        val boom = RuntimeException("no-prev-handler")
        Thread.getDefaultUncaughtExceptionHandler()
            ?.uncaughtException(Thread.currentThread(), boom)

        val latest = store.latestCrashFile()
        assertNotNull(latest)
        assertTrue(latest!!.readText().contains("no-prev-handler"))
    }

    @Test fun `cacheDir starts empty`() {
        // Sanity-check the @Before reset to make sure other tests don't
        // bleed state through the shared cache dir.
        assertNull(store.latestCrashFile())
        assertEquals(0, store.allCrashFiles().size)
    }
}

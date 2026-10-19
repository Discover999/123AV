package com.av123.video.data.diagnostics

import com.av123.video.data.net.AppErrorKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DiagnosticsMetricsTest {

    @Before
    fun setUp() {
        DiagnosticsMetrics.reset()
    }

    @After
    fun tearDown() {
        DiagnosticsMetrics.reset()
    }

    @Test
    fun snapshot_withoutAttempts_hasNoRate() {
        val snapshot = DiagnosticsMetrics.snapshot()

        assertEquals(0, snapshot.captureAttempts)
        assertEquals(0, snapshot.captureSuccesses)
        assertNull(snapshot.captureSuccessPercent)
        assertEquals(0, snapshot.failureTotal)
    }

    @Test
    fun snapshot_computesCaptureRateAndFailureTotals() {
        DiagnosticsMetrics.recordCaptureAttempt()
        DiagnosticsMetrics.recordCaptureAttempt()
        DiagnosticsMetrics.recordCaptureSuccess()
        DiagnosticsMetrics.recordLoadFailure(AppErrorKind.NETWORK)
        DiagnosticsMetrics.recordLoadFailure(AppErrorKind.PARSE)

        val snapshot = DiagnosticsMetrics.snapshot()

        assertEquals(2, snapshot.captureAttempts)
        assertEquals(1, snapshot.captureSuccesses)
        assertEquals(50, snapshot.captureSuccessPercent)
        assertEquals(1, snapshot.captureFailures)
        assertEquals(2, snapshot.failureTotal)
        assertEquals(1, snapshot.failuresByKind[AppErrorKind.NETWORK])
        assertEquals(1, snapshot.failuresByKind[AppErrorKind.PARSE])
    }
}

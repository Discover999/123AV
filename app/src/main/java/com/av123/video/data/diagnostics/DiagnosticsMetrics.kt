package com.av123.video.data.diagnostics

import com.av123.video.data.net.AppErrorKind
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 进程内诊断指标：抓流尝试/成功与加载失败分类。
 * 仅用于诊断页“本次运行”展示，不持久化，进程重启清零。
 */
object DiagnosticsMetrics {

    private val captureAttempts = AtomicInteger()
    private val captureSuccesses = AtomicInteger()
    private val failures = ConcurrentHashMap<AppErrorKind, AtomicInteger>()

    fun recordCaptureAttempt() {
        captureAttempts.incrementAndGet()
    }

    fun recordCaptureSuccess() {
        captureSuccesses.incrementAndGet()
    }

    fun recordLoadFailure(kind: AppErrorKind) {
        failures.getOrPut(kind) { AtomicInteger() }.incrementAndGet()
    }

    fun snapshot(): DiagnosticsMetricsSnapshot = DiagnosticsMetricsSnapshot(
        captureAttempts = captureAttempts.get(),
        captureSuccesses = captureSuccesses.get(),
        failuresByKind = failures.mapValues { it.value.get() }
    )

    internal fun reset() {
        captureAttempts.set(0)
        captureSuccesses.set(0)
        failures.clear()
    }
}

data class DiagnosticsMetricsSnapshot(
    val captureAttempts: Int = 0,
    val captureSuccesses: Int = 0,
    val failuresByKind: Map<AppErrorKind, Int> = emptyMap()
) {
    /** 抓流成功率（0–100）；本次没有尝试时为 null，UI 显示“—” */
    val captureSuccessPercent: Int? =
        if (captureAttempts > 0) captureSuccesses * 100 / captureAttempts else null

    val captureFailures: Int = (captureAttempts - captureSuccesses).coerceAtLeast(0)

    val failureTotal: Int = failuresByKind.values.sum()
}

package com.av123.video.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 冷启动基线：每次先回桌面，再以 COLD 模式启动应用。
 * 结果由 Macrobenchmark 输出，供 docs/p4-baseline.md 记录。
 *
 * 采用 3 轮 + [CompilationMode.None]，避免默认 5 轮 AOT 编译在真机上耗时数分钟；
 * 需要 release 编译态的严格对比时，可改回 CompilationMode.DEFAULT。
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = 3,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), 5_000)
    }

    private companion object {
        const val PACKAGE_NAME = "com.av123.video"
    }
}

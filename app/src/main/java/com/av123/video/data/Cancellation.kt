package com.av123.video.data

import kotlinx.coroutines.CancellationException

/**
 * 与 `runCatching` 相同，但不会把协程取消误当成业务失败。
 * 网络/解析路径统一使用它，保证 ViewModel/LaunchedEffect 取消能向上传播。
 */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

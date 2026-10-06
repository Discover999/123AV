package com.av123.video.data.net

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * 瞬时错误事件：由根 Scaffold 的全局 Snackbar 消费。
 * 与页内整屏 ErrorState 的区别：页面已有可展示内容（下拉刷新失败、翻页失败、
 * 切换筛选失败、详情网络回退缓存等）时，不打断当前页面，仅顶部/底部轻提示。
 */
data class ErrorEvent(
    val id: Long,
    val error: AppError
)

/**
 * 全局错误事件总线（进程级单例，挂在 AppContainer）。
 * - replay = 0：错误是瞬时事件，不向迟到订阅者重放；
 * - extraBufferCapacity：网络线程 tryEmit 不阻塞，缓冲积压时丢弃最旧事件。
 */
class ErrorReporter {

    private val sequence = AtomicLong(0)

    private val _events = MutableSharedFlow<ErrorEvent>(
        replay = 0,
        extraBufferCapacity = 8
    )
    val events: SharedFlow<ErrorEvent> = _events.asSharedFlow()

    fun report(error: AppError) {
        _events.tryEmit(ErrorEvent(sequence.incrementAndGet(), error))
    }

    fun report(throwable: Throwable) {
        report(AppError.from(throwable))
    }
}

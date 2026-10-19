package com.av123.video.data

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellationTest {

    @Test
    fun runCatchingCancellable_rethrowsCancellation() {
        val error = kotlin.runCatching {
            runCatchingCancellable<Unit> { throw CancellationException("cancel") }
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
    }
}

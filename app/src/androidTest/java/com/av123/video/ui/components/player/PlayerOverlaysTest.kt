package com.av123.video.ui.components.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose 冒烟：播放失败浮层必须同时出现标题与重播按钮。 */
@RunWith(AndroidJUnit4::class)
class PlayerOverlaysTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun errorOverlay_showsFailureAndRetry() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ErrorOverlay(
                error = null,
                onRetry = {},
                modifier = Modifier.fillMaxSize()
            )
        }
        composeRule.mainClock.advanceTimeBy(500)

        composeRule.onNodeWithText("播放失败").assertIsDisplayed()
        composeRule.onNodeWithText("重新播放").assertIsDisplayed()
    }
}

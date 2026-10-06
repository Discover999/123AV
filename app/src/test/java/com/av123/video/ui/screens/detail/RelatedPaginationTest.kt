package com.av123.video.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 相关推荐“加载更多”分页纯函数测试。
 */
class RelatedPaginationTest {

    @Test
    fun nextVisible_appendsOnePage() {
        // 站点实测：第一页 6 条、池共 12 条 -> 展开后 12
        assertEquals(12, relatedNextVisible(currentVisible = 6, total = 12, pageSize = 6))
    }

    @Test
    fun nextVisible_capsAtTotalForOddRemainder() {
        // 池大小不是页大小整数倍时，最后一次展开停在总量（不多不少）
        assertEquals(7, relatedNextVisible(currentVisible = 6, total = 7, pageSize = 6))
        assertEquals(13, relatedNextVisible(currentVisible = 12, total = 13, pageSize = 6))
    }

    @Test
    fun nextVisible_alreadyAllVisibleStaysAtTotal() {
        // 已全部展示时再计算也不越界
        assertEquals(12, relatedNextVisible(currentVisible = 12, total = 12, pageSize = 6))
    }
}

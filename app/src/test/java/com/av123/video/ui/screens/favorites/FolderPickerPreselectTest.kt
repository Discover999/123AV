package com.av123.video.ui.screens.favorites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 文件夹选择器“预选共有收藏夹”纯函数测试。
 */
class FolderPickerPreselectTest {

    @Test
    fun uniqueCommonGroup_isPreselected() {
        // 两个视频都在文件夹 5（各自另有其他归属）→ 唯一共有 5
        val result = commonSingleGroup(
            listOf(setOf(5L, 9L), setOf(2L, 5L))
        )
        assertEquals(5L, result)
    }

    @Test
    fun noCommonGroup_returnsNull() {
        assertNull(commonSingleGroup(listOf(setOf(1L), setOf(2L))))
        // 单个视频不属于任何收藏夹
        assertNull(commonSingleGroup(listOf(emptySet())))
    }

    @Test
    fun multipleCommonGroups_returnsNull() {
        // 同属两个文件夹：不预选，交用户明确选择
        assertNull(commonSingleGroup(listOf(setOf(1L, 2L), setOf(1L, 2L, 3L))))
    }

    @Test
    fun emptyInput_returnsNull() {
        assertNull(commonSingleGroup(emptyList()))
    }
}

package com.av123.video.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryFilterTest {

    @Test
    fun fromLabel_mapsAllToSentinelWithoutCategoryKey() {
        val filter = CategoryFilter.fromLabel("全部")

        assertEquals(CategoryFilter.All, filter)
        assertEquals("全部", filter.label)
        assertNull("“全部”不对应站点列表页", filter.categoryKey)
    }

    @Test
    fun fromLabel_mapsNamedCategoryToKeyAndLabel() {
        val filter = CategoryFilter.fromLabel("最近添加")

        assertEquals(CategoryFilter.Named("最近添加"), filter)
        assertEquals("最近添加", filter.categoryKey)
        assertEquals("最近添加", filter.label)
    }

    @Test
    fun allIsSingletonAndNamedEqualityIsByValue() {
        assertEquals(CategoryFilter.All, CategoryFilter.fromLabel("全部"))
        assertEquals(CategoryFilter.Named("新发布"), CategoryFilter.Named("新发布"))
    }
}

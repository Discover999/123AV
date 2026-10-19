package com.av123.video.ui.screens.home

/**
 * 首页分类筛选：
 * - [All] 展示首页聚合内容（精选/热门/最新/继续观看），不请求站点分类列表页；
 * - [Named] 对应站点分类（如“新发布”“最近添加”），进入时按需加载其列表页。
 */
sealed interface CategoryFilter {
    data object All : CategoryFilter

    data class Named(val value: String) : CategoryFilter

    /** 分类列表页与磁盘缓存使用的站点分类名；“全部”没有对应列表页，返回 null */
    val categoryKey: String?
        get() = (this as? Named)?.value

    /** 胶囊标签文案 */
    val label: String
        get() = categoryKey ?: ALL_LABEL

    companion object {
        const val ALL_LABEL = "全部"

        /** 站点解析出的分类名转成类型化筛选；“全部”是唯一哨兵 */
        fun fromLabel(label: String): CategoryFilter =
            if (label == ALL_LABEL) All else Named(label)
    }
}

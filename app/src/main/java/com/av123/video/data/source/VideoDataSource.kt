package com.av123.video.data.source

import com.av123.video.data.model.CategoryPage
import com.av123.video.data.model.HomeFeed
import com.av123.video.data.model.Video

/**
 * 视频数据源抽象。
 *
 * 本 App 没有官方 API，所有数据都需要从网页读取/解析后填充，
 * 当前实现为 [JsoupVideoDataSource]：用 Jsoup 抓取网页并映射为 [Video]。
 */
interface VideoDataSource {

    /** 拉取首页聚合数据（分类 / 精选 / 热门 / 最新） */
    suspend fun fetchHomeFeed(): HomeFeed

    /**
     * 按分类拉取视频列表（分页）。
     * 返回该页视频列表 + 页脚分页信息（站点无 .pager 结构时 pagination 为 null）。
     */
    suspend fun fetchByCategory(category: String, page: Int): CategoryPage

    /**
     * 按首页分栏导航路径拉取视频列表（分页）。
     * [path] 为 nav 解析出的站点相对路径（含语言前缀），如 /cn/hot、/cn/all?sort=today、
     * /cn/11、/cn/genres；[label] 为该栏目的展示名，用作列表卡片的分类标记。
     * [params] 为顶部筛选表单提交的查询参数（type/year/actress/sort 等），
     * 与 path 自带查询串合并（同名以 [params] 为准），空值参数不下发。
     */
    suspend fun fetchByPath(
        path: String,
        label: String,
        page: Int,
        params: Map<String, String> = emptyMap()
    ): CategoryPage

    /**
     * 拉取视频详情（标题、详情字段、剧集数、播放地址等，通常需要进入详情页二次解析）。
     * [reference] 为列表页得到的条目，提供详情页链接（sourcePageUrl / detailPath）；
     * 列表缓存未命中时只保证 id 有效。
     *
     * 同时返回同一份详情文档里**静态可见**的相关推荐卡片（非 `<template>` 原型卡）：
     * 命中时 UI 无需再创建无头 WebView 兜底；为空才由调用方走渲染兜底。
     */
    suspend fun fetchVideoDetail(reference: Video): VideoDetail

    /**
     * 关键词搜索（分页）。
     * 返回该页视频列表 + 页脚分页信息（站点无 .pager 结构时 pagination 为 null）
     * + 页面头部声明的结果总数（如 .pagehead__count 的“960个视频”，无则为 null）。
     */
    suspend fun search(keyword: String, page: Int = 1): CategoryPage

    /**
     * 详情页“相关推荐”：无头 WebView 渲染详情页后回传页面 HTML，
     * 由此解析其中的视频卡片。纯本地解析、不发起网络请求；
     * 解析失败/无候选由调用方按空列表处理，不影响详情主流程。
     *
     * @param html WebView evaluateJavascript 回传的卡片 outerHTML 拼接/整页 HTML
     * @param baseUrlHint 详情页绝对地址，用于把相对 href/img 补全为绝对地址
     */
    suspend fun parseRelatedFromRenderedHtml(html: String, baseUrlHint: String): List<Video>
}

/**
 * 详情页抓取结果：
 * @property video 详情主体（页面无有效内容时为 null）
 * @property relatedStatic 同一份详情文档中静态可见的相关推荐（未去重/未截断，
 *           由仓库层统一 dedupRelated）；站点推荐位多为 JS 动态渲染时为空
 */
data class VideoDetail(
    val video: Video?,
    val relatedStatic: List<Video> = emptyList()
)

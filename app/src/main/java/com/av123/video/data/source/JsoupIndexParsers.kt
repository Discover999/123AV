package com.av123.video.data.source

import com.av123.video.data.model.ActressProfile
import com.av123.video.data.model.CategoryPage
import com.av123.video.data.model.Episode
import com.av123.video.data.model.FilterSearchField
import com.av123.video.data.model.HomeFeed
import com.av123.video.data.model.IndexEntry
import com.av123.video.data.model.NavChannel
import com.av123.video.data.model.NavChannelGroup
import com.av123.video.data.model.Pagination
import com.av123.video.data.model.Video
import com.av123.video.data.model.FilterGroup
import com.av123.video.data.model.FilterOption
import com.av123.video.data.db.AppJson
import com.av123.video.data.model.InfoChip
import com.av123.video.data.model.VideoInfoRow
import com.av123.video.data.net.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * 解析索引聚合页的条目网格（.ggrid > a.gchip，类别页）：
 * 条目名取 .gchip__name，数量文案取 .gchip__count（如 176,566），
 * href 为条目视频流路径（已含 /cn 前缀）。页面无 .ggrid 时返回空列表。
 */
internal fun JsoupVideoDataSource.parseIndexEntries(doc: Document): List<IndexEntry> {
    val grid = doc.selectFirst(config.indexGridSelector) ?: return emptyList()
    return grid.select(config.indexChipSelector).mapNotNull { a ->
        val href = a.attr("href").trim()
        val name = a.selectFirst(config.indexChipName)?.text()?.trim().orEmpty()
        if (href.isBlank() || href == "#" || name.isBlank()) return@mapNotNull null
        IndexEntry(
            name = name,
            path = href,
            count = a.selectFirst(config.indexChipCount)?.text()?.trim().orEmpty()
        )
    }
}

/**
 * 解析索引聚合页的排行行（.slist > a.srow，系列页）：
 * 序号取 .srow__rank（如 01），名称取 .srow__name，
 * 副文案取 .srow__meta（如“4,476个视频 · 4.2M次观看”），整行 href 为视频流路径。
 * 行内的关注按钮（.follow-btn）是 Alpine 交互，不解析。页面无 .slist 时返回空列表。
 */
internal fun JsoupVideoDataSource.parseIndexRows(doc: Document): List<IndexEntry> {
    val list = doc.selectFirst(config.indexListSelector) ?: return emptyList()
    return list.select(config.indexRowSelector).mapNotNull { a ->
        val href = a.attr("href").trim()
        val name = a.selectFirst(config.indexRowName)?.text()?.trim().orEmpty()
        if (href.isBlank() || href == "#" || name.isBlank()) return@mapNotNull null
        IndexEntry(
            name = name,
            path = href,
            meta = a.selectFirst(config.indexRowMeta)?.text()?.trim().orEmpty(),
            rank = a.selectFirst(config.indexRowRank)?.text()?.trim()
        )
    }
}

/** background-image: url('...') 中提取图片地址的正则（兼容单/双引号/无引号） */
private val bgImageUrlPattern = Regex("""url\(\s*['"]?([^'")]+)['"]?\s*\)""")

/**
 * 解析索引聚合页的演员卡（.agrid > .actress，演员页）：
 * 名字与路径取 .actress__name（href 已含 /cn 前缀），头像取 .actress__avatar
 * 内联样式 background-image 的 url(...)；统计取 .actress__meta 内各 span
 * （span 的 title 为“视频/观看/关注者”，文本为数值），分别落入结构化字段；
 * 关注按钮 .actress__follow 内 span 是紧凑关注数（如 16.3K）。
 * 关注/取关交互是 Alpine 行为，不解析。页面无 .agrid 时返回空列表。
 */
internal fun JsoupVideoDataSource.parseActresses(doc: Document): List<IndexEntry> {
    val grid = doc.selectFirst(config.indexActressGridSelector) ?: return emptyList()
    return grid.select(config.indexActressSelector).mapNotNull { card ->
        val nameLink = card.selectFirst(config.indexActressName)
        val href = nameLink?.attr("href")?.trim().orEmpty()
        val name = nameLink?.text()?.trim().orEmpty()
        if (href.isBlank() || href == "#" || name.isBlank()) return@mapNotNull null

        val avatarStyle = card.selectFirst(config.indexActressAvatar)
            ?.attr("style")?.trim().orEmpty()
        val avatarUrl = bgImageUrlPattern.find(avatarStyle)
            ?.groupValues?.get(1)?.trim()
            // 容错：去掉可能混入的引号/反引号包裹
            ?.trim('\'', '"', '`')
            .orEmpty()

        // <span title="视频">…3,100</span>：title→数值
        val stats = card.select("${config.indexActressMeta} span")
            .mapNotNull { span ->
                val label = span.attr("title").trim()
                val value = span.ownText().trim()
                if (label.isBlank() || value.isBlank()) null else label to value
            }
            .toMap()

        IndexEntry(
            name = name,
            path = href,
            avatarUrl = avatarUrl,
            videos = stats["视频"].orEmpty(),
            views = stats["观看"].orEmpty(),
            followers = stats["关注者"].orEmpty(),
            followersLabel = card.selectFirst(config.indexActressFollow)?.text()?.trim().orEmpty()
        )
    }
}

/**
 * 解析女演员个人页头部资料（.ahero，路径 /cn/actresses/<slug>）：
 * 头像取 .ahero__avatar 内联样式 background-image 的 url(...)；
 * 属性行取 .ahero__meta 原文（如“38岁 · 163cm · 88-59-85 · E罩杯”）；
 * .ahero__stats 内每个 .astat：b=数值、span=标签（视频/观看/关注者/出道）。
 * 关注按钮的紧凑粉丝数是 Alpine x-text 渲染（静态 HTML 为空），
 * 直接复用“关注者”统计原文（如 16.3K）。页面无 .ahero 时返回 null。
 */
internal fun JsoupVideoDataSource.parseActressProfile(doc: Document): ActressProfile? {
    val hero = doc.selectFirst(config.actressHeroSelector) ?: return null
    val name = hero.selectFirst(config.actressHeroName)?.text()?.trim().orEmpty()
    if (name.isBlank()) return null

    val avatarStyle = hero.selectFirst(config.actressHeroAvatar)
        ?.attr("style")?.trim().orEmpty()
    val avatarUrl = bgImageUrlPattern.find(avatarStyle)
        ?.groupValues?.get(1)?.trim()
        ?.trim('\'', '"', '`')
        .orEmpty()

    val stats = hero.select(config.actressHeroStatSelector).mapNotNull { stat ->
        val value = stat.selectFirst("b")?.text()?.trim().orEmpty()
        val label = stat.selectFirst("span")?.text()?.trim().orEmpty()
        if (label.isBlank()) null else label to value
    }.toMap()
    val followers = stats["关注者"].orEmpty()

    return ActressProfile(
        name = name,
        avatarUrl = avatarUrl,
        meta = hero.selectFirst(config.actressHeroMeta)?.text()?.trim().orEmpty(),
        videos = stats["视频"].orEmpty(),
        views = stats["观看"].orEmpty(),
        followers = followers,
        debut = stats["出道"].orEmpty(),
        followersLabel = followers
    )
}

/**
 * 解析索引聚合页的制作商卡（.mgrid > a.mcard，制作商页）：
 * href 为制作商视频流路径（如 /cn/makers/movies，已含 /cn 前缀）；
 * 名称取 .mcard__name，logo 徽标文案取 .mcard__logo（如 "movies"），
 * 副文案取 .mcard__meta（如“33,419个视频”，已是完整文案直接展示）。
 * 行内关注按钮（.follow-btn）是 Alpine 交互，不解析。页面无 .mgrid 时返回空列表。
 */
internal fun JsoupVideoDataSource.parseMakers(doc: Document): List<IndexEntry> {
    val grid = doc.selectFirst(config.indexMakerGridSelector) ?: return emptyList()
    return grid.select(config.indexMakerSelector).mapNotNull { a ->
        val href = a.attr("href").trim()
        val name = a.selectFirst(config.indexMakerName)?.text()?.trim().orEmpty()
        if (href.isBlank() || href == "#" || name.isBlank()) return@mapNotNull null
        IndexEntry(
            name = name,
            path = href,
            logo = a.selectFirst(config.indexMakerLogo)?.text()?.trim().orEmpty(),
            meta = a.selectFirst(config.indexMakerMeta)?.text()?.trim().orEmpty()
        )
    }
}

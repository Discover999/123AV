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

internal data class PlayerData(
    val episodes: List<PlayerEpisode>,
    val poster: String,
    val code: String
)
internal data class PlayerEpisode(val name: String, val url: String)

private val playerJsonRegex = Regex("""player\(\s*JSON\.parse\('([^']+)'\)""")

/** player(JSON.parse('...'), 数字id, '影片代号', ...) 中第三个参数的代号，如 'siro-5733' */
private val playerCodeRegex =
    Regex("""player\(\s*JSON\.parse\('[^']*'\)\s*,\s*\d+\s*,\s*'([^']*)'""")

/**
 * 从详情页 HTML 提取 player(JSON.parse('...')) 里的剧集数组：
 * JS 字符串里的 \u0022 还原为双引号、\/ 还原为斜杠后按 JSON 解析；
 * 首集 url 的 poster 查询参数即详情页海报封面。
 */
internal fun JsoupVideoDataSource.parsePlayerData(html: String): PlayerData {
    val code = playerCodeRegex.find(html)?.groupValues?.get(1)?.trim().orEmpty()
    val raw = playerJsonRegex.find(html)?.groupValues?.get(1)
        ?.replace("""\u0022""", "\"")
        ?.replace("""\/""", "/")
        ?: return PlayerData(emptyList(), "", code)
    val episodes = runCatching {
        AppJson.parseToJsonElement(raw).jsonArray.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            // 播放页地址经 HTML 属性 -> JS 字符串 -> JSON 三层转义，
            // JSON 解析后仍可能残留 "\/"、"\u0026"，必须还原，
            // 否则 WebView 会把反斜杠规整成斜杠，拼出 //e//xxx 错误路径
            val url = unescapePlayerUrl(
                obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
            if (url.isBlank()) {
                null
            } else {
                PlayerEpisode(obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), url)
            }
        }
    }.getOrDefault(emptyList())
    val poster = episodes.firstOrNull()?.let { ep ->
        SiteUrls.queryParameter(ep.url, "poster")
    }.orEmpty()
    return PlayerData(episodes, poster, code)
}

/**
 * 播放页地址多层转义还原：HTML 属性 → JS 单引号字符串 → JSON 三层嵌套后，
 * org.json 解析出的地址仍可能残留字面量 "\/"（反斜杠+斜杠）与 "\u0026"（&）。
 * 不还原的话 WebView 会把 "\" 按 "/" 规整，拼出 //e//xxx 错误路径导致播放页报错。
 */
private fun unescapePlayerUrl(raw: String): String =
    raw.replace("\\/", "/")
        .replace("\\u0026", "&", ignoreCase = true)

/**
 * 从 .player 容器的 style="background-image:url('...')" 提取海报。
 * 页面实际写法可能混用单引号与反引号（url('`https://...`')），
 * 故先取 url(...) 括号内全部内容，再剔除首尾的引号 / 反引号 / 空白。
 */
internal fun parsePlayerBackground(doc: Document): String =
    doc.selectFirst(".player")?.attr("style")
        ?.let { Regex("""url\(\s*([^)]*)\)""").find(it)?.groupValues?.get(1) }
        ?.trim()
        ?.trim('\'', '"', '`')
        .orEmpty()

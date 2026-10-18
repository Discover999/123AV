package com.av123.video.data.source

/**
 * P2.2 站点适配器：对外只暴露 [VideoSite] 契约。
 *
 * 目前解析/抓取仍由 [JsoupVideoDataSource] 承担，本类负责站点身份与配置版本；
 * 后续把解析逻辑迁入适配器时，只需替换 delegate，仓库层与 UI 不受影响。
 */
class JsoupSiteAdapter(
    private val delegate: VideoSite,
    config: SiteConfig
) : VideoSite by delegate {

    override val configVersion: String = "${config.id}@${config.version}"
}

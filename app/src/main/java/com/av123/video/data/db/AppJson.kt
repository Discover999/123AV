package com.av123.video.data.db

import kotlinx.serialization.json.Json

/**
 * 应用内统一的 JSON 配置：
 * - ignoreUnknownKeys：远程站点配置/旧缓存中出现新字段或已删字段时不崩，向前/向后兼容；
 * - explicitNulls=false：缺省字段直接走数据类默认值，缓存 JSON 更短；
 * - encodeDefaults=false：只写非默认值。
 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}

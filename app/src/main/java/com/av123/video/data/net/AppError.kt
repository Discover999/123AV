package com.av123.video.data.net

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 归一化后的错误类别。数据层只产出类别，不直接拼用户文案，
 * 由 UI 层按 stringResource 解析为中文提示，
 * 避免把 "connect timed out"、"HTTP error fetching URL" 之类英文异常信息暴露给用户。
 */
enum class AppErrorKind {
    /** DNS 解析失败（断网/域名被墙最常见） */
    DNS,

    /** 请求/读取超时 */
    TIMEOUT,

    /** HTTPS 证书校验失败 */
    SSL,

    /** 连接被拒、网络中断等其它 IO 错误 */
    NETWORK,

    /** 4xx：请求侧错误 */
    HTTP_CLIENT,

    /** 5xx：服务端错误 */
    HTTP_SERVER,

    /** 响应已拿到但解析失败（站点结构变更等） */
    PARSE,

    /** 未归类错误 */
    UNKNOWN
}

/**
 * 全局统一错误描述。
 * [httpCode] 仅在 [kind] 为 [AppErrorKind.HTTP_CLIENT]/[AppErrorKind.HTTP_SERVER] 时存在。
 */
data class AppError(
    val kind: AppErrorKind,
    val httpCode: Int? = null
) {
    companion object {
        fun from(throwable: Throwable): AppError = when (throwable) {
            is HttpStatusException -> when (throwable.code) {
                in 400..499 -> AppError(AppErrorKind.HTTP_CLIENT, throwable.code)
                else -> AppError(AppErrorKind.HTTP_SERVER, throwable.code)
            }
            is UnknownHostException -> AppError(AppErrorKind.DNS)
            is SocketTimeoutException -> AppError(AppErrorKind.TIMEOUT)
            is SSLException -> AppError(AppErrorKind.SSL)
            is ConnectException -> AppError(AppErrorKind.NETWORK)
            is IOException -> AppError(AppErrorKind.NETWORK)
            else -> AppError(AppErrorKind.UNKNOWN)
        }
    }
}

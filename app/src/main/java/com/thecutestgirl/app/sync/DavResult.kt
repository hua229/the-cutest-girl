package com.thecutestgirl.app.sync

import java.io.IOException

/**
 * WebDAV 错误模型（P2-3）：把 401/404/507/网络/解析区分开。
 * 以前全返回 Boolean?/null，密码错误和网络断开看起来一模一样，Worker 还会无限重试。
 */
sealed class DavError {
    object Auth : DavError()
    object NotFound : DavError()
    object Quota : DavError()
    object Parse : DavError()
    data class Network(val message: String) : DavError()

    /** 给用户看的短句；不要只说「失败」 */
    fun userMessage(): String = when (this) {
        Auth -> "账号或应用密码不正确"
        NotFound -> "服务器地址不对，或云端目录不存在"
        Quota -> "云端空间不足"
        Parse -> "云端数据格式异常"
        is Network -> message.ifBlank { "网络错误" }
    }

    /** 认证/地址/配额/数据问题重试也没用；只有网络类值得退避重试 */
    fun retryable(): Boolean = this is Network
}

sealed class DavResult<out T> {
    data class Ok<T>(val value: T) : DavResult<T>()
    data class Err(val error: DavError) : DavResult<Nothing>()
}

/** 把 DavResult 转成异常，交给 syncOnce 的统一 catch 处理 */
internal fun <T> DavResult<T>.getOrThrowDav(): T = when (this) {
    is DavResult.Ok -> value
    is DavResult.Err -> throw DavException(error)
}

internal class DavException(val error: DavError) : IOException(error.userMessage())
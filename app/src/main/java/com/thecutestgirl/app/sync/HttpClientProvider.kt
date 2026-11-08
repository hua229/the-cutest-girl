package com.thecutestgirl.app.sync

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全进程共享一个 OkHttpClient（P1-PERF-3）。
 *
 * 原来 [WebDavClient] 每次构造都 new 一个 client：前台 30 秒一轮同步时，
 * 连接池和线程池完全无法复用，TLS 握手和线程创建是纯浪费。
 * 认证仍是每个请求带 Authorization header，不放这里。
 *
 * callTimeout 90s：一次挂起的请求不能把同步 Mutex 占住几分钟。
 */
internal object HttpClientProvider {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}
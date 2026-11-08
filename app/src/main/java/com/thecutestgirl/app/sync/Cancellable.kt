package com.thecutestgirl.app.sync

import kotlinx.coroutines.CancellationException

/**
 * 可取消的 runCatching（P2-4）：
 * 协程取消不是「失败」，必须原样抛出，否则已取消的同步会继续跑。
 */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
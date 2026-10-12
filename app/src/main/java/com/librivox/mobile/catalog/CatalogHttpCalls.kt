package com.librivox.mobile.catalog

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal inline fun <T> catalogAttempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}

/** Cancelling a search closes its sockets, including a response body still being read. */
internal suspend fun OkHttpClient.executeCatalogRequest(request: Request): CatalogHttpResponse {
    currentCoroutineContext().ensureActive()
    return suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        CatalogHttpResponse(
                            statusCode = it.code,
                            body = it.body?.string().orEmpty(),
                            headers = it.headers.names().associateWith(it.headers::values),
                        )
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (failure: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }
            }
        })
    }
}

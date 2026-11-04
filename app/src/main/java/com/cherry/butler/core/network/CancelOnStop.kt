package com.cherry.butler.core.network

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import okhttp3.Call

/**
 * Cuts [call] the moment [job] is cancelled (Stop), not when it has finished: a blocking read
 * waiting on the network doesn't notice a cancel until the next bytes come, and an ordinary
 * completion handler runs only after it does. Cutting the connection ends that read at once.
 * Dispose the handle when the call is done.
 */
@OptIn(InternalCoroutinesApi::class)
fun Job.cancelOnStop(call: Call) = invokeOnCompletion(onCancelling = true, invokeImmediately = true) { call.cancel() }

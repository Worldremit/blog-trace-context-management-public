package com.worldremit.base.tracing

import kotlin.coroutines.CoroutineContext

data class TracingContext(val value: Map<String, String>)

typealias CaptureTracingContext = () -> TracingContext

typealias RunInTracingContext<T> = (TracingContext, () -> T) -> T

typealias CreateTracingCoroutineContext = (TracingContext) -> CoroutineContext


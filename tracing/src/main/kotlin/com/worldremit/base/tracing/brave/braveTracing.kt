package com.worldremit.base.tracing.brave

import arrow.core.computations.nullable
import arrow.core.getOrElse
import arrow.core.toOption
import brave.Request
import brave.Span
import brave.Tracing
import brave.propagation.CurrentTraceContext
import brave.propagation.CurrentTraceContext.Scope
import brave.propagation.Propagation
import brave.propagation.TraceContext
import com.worldremit.base.tracing.TracingContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class MapRequest(initial: Map<String, String> = mapOf()) : Request() {
    private val mapping: MutableMap<String, String> = initial.toMutableMap()

    override fun spanKind(): Span.Kind = Span.Kind.SERVER

    override fun unwrap(): Any = context

    val context: Map<String, String> get() = mapping.toMap()

    fun setContext(key: String, value: String) {
        mapping[key] = value
    }

    fun getContext(key: String): String? = mapping[key]
}

object MapRequestGetter : Propagation.Getter<MapRequest, String> {
    override fun get(request: MapRequest, key: String): String? = request.getContext(key)
}

object MapRequestSetter : Propagation.Setter<MapRequest, String> {
    override fun put(request: MapRequest, key: String, value: String) {
        request.setContext(key, value)
    }
}

fun captureBraveTracingContext(tracing: Tracing): TracingContext {
    val traceContext = tracing.currentTraceContext().get() ?: return TracingContext(emptyMap())
    val request = MapRequest()
    tracing.propagation().injector(MapRequestSetter).inject(traceContext, request)
    return TracingContext(request.context)
}

typealias CreateTraceContext = (Tracing, TracingContext) -> TraceContext?

fun createBraveTraceContext(tracing: Tracing, context: TracingContext): TraceContext? =
    tracing.propagation().extractor(MapRequestGetter)
        .extract(MapRequest(context.value))
        .context()

fun <R> runInTracingContext(
    createTraceContext: CreateTraceContext,
    tracing: Tracing,
    tracingContext: TracingContext,
    block: () -> R
): R {
    val restoredScope = nullable.eager<Scope> {
        val context = createTraceContext(tracing, tracingContext).bind()
        val currentTraceContext = currentTraceContext().bind()
        currentTraceContext.newScope(context)
    }
    val activeScope = restoredScope ?: Scope.NOOP
    return activeScope.use {
        block()
    }
}

fun createBraveTracingCoroutineContext(
    createTraceContext: CreateTraceContext,
    tracing: Tracing,
    context: TracingContext
): CoroutineContext =
    createTraceContext(tracing, context)
        .toOption()
        .map(::TraceContextElement)
        .getOrElse { EmptyCoroutineContext }


fun traceContext(): TraceContext? = currentTraceContext()?.get()

fun currentTraceContext(): CurrentTraceContext? = Tracing.current()?.currentTraceContext()

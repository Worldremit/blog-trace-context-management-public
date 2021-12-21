package com.worldremit.base.tracing.brave

import brave.propagation.CurrentTraceContext.Scope
import brave.propagation.TraceContext
import kotlinx.coroutines.ThreadContextElement
import mu.KotlinLogging
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class TraceContextElement(
    val context: TraceContext? = traceContext()
) : ThreadContextElement<Scope>, AbstractCoroutineContextElement(Key) {
    private val logger = KotlinLogging.logger {}

    companion object Key : CoroutineContext.Key<TraceContextElement>

    init {
        logger.debug("Initialized to: <$context> in thread - id: <${currentThread().id}>, name: <${currentThread().name}>")
    }

    override fun updateThreadContext(context: CoroutineContext): Scope {
        val scope = currentTraceContext()?.newScope(this.context) ?: Scope.NOOP
        logger.debug("Registered new scope: <$scope> for traceContext: <${this.context}> in thread - id: <${currentThread().id}>, name: <${currentThread().name}>")
        return scope
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: Scope) {
        logger.debug("Restoring traceContext by closing scope: <$oldState> in thread - id: <${currentThread().id}>, name: <${currentThread().name}>")
        oldState.close()
    }

    private fun currentThread() = Thread.currentThread()
}

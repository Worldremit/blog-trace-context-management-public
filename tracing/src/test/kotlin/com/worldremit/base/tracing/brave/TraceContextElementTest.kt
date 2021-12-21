package com.worldremit.base.tracing.brave

import brave.Tracing
import brave.propagation.CurrentTraceContext
import brave.propagation.CurrentTraceContext.Scope
import brave.propagation.TraceContext
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

internal class TraceContextElementTest {
    @Test
    fun `read traceContext from Tracing on construction`() {
        mockkStatic(Tracing::class)
        val tracing: Tracing = mockk()
        val currentTraceContext: CurrentTraceContext = mockk()
        val traceContext: TraceContext = mockk()
        every { Tracing.current() } returns tracing
        every { tracing.currentTraceContext() } returns currentTraceContext
        every { currentTraceContext.get() } returns traceContext

        TraceContextElement()

        verify { currentTraceContext.get() }
    }

    @Test
    fun `create new scope on updateThreadContext call`() {
        mockkStatic(Tracing::class)
        val tracing: Tracing = mockk()
        val currentTraceContext: CurrentTraceContext = mockk()
        val context: TraceContext = mockk()
        val element = TraceContextElement(context)
        val scope: Scope = mockk()
        every { Tracing.current() } returns tracing
        every { tracing.currentTraceContext() } returns currentTraceContext
        every { currentTraceContext.newScope(context) } returns scope

        element.updateThreadContext(mockk())

        verify { currentTraceContext.newScope(context) }
    }

    @Test
    fun `closes scope on xx call`() {
        val context: TraceContext = mockk()
        val element = TraceContextElement(context)
        val scope: Scope = mockk()
        every { scope.close() } just runs
        
        element.restoreThreadContext(mockk(), scope)

        verify { scope.close() }
    }

    companion object {

        @AfterAll
        @JvmStatic
        fun cleanUp() {
            unmockkStatic(Tracing::class)
        }
    }
}


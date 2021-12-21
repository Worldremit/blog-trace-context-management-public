package com.worldremit.base.tracing.brave

import brave.Tracing
import brave.propagation.CurrentTraceContext
import brave.propagation.TraceContext
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

internal class TraceContextFunctionTest {
    @Test
    fun `return null when current Tracing is null`() {
        mockkStatic(Tracing::class)
        every { Tracing.current() } returns null

        traceContext().shouldBeNull()
    }

    @Test
    fun `return null when currentTracingContext is null`() {
        mockkStatic(Tracing::class)
        val tracing: Tracing = mockk()
        every { Tracing.current() } returns tracing
        every { tracing.currentTraceContext() } returns null

        traceContext().shouldBeNull()
    }

    @Test
    fun `return null when traceContext is null`() {
        mockkStatic(Tracing::class)
        val tracing: Tracing = mockk()
        val currentTraceContext: CurrentTraceContext = mockk()
        every { Tracing.current() } returns tracing
        every { tracing.currentTraceContext() } returns currentTraceContext
        every { tracing.currentTraceContext().get() } returns null

        traceContext().shouldBeNull()
    }

    @Test
    fun `return traceContext`() {
        mockkStatic(Tracing::class)
        val tracing: Tracing = mockk()
        val currentTraceContext: CurrentTraceContext = mockk()
        val traceContext: TraceContext = mockk()
        every { Tracing.current() } returns tracing
        every { tracing.currentTraceContext() } returns currentTraceContext
        every { tracing.currentTraceContext().get() } returns traceContext

        traceContext() shouldBe traceContext
    }

    companion object {

        @JvmStatic
        @AfterAll
        fun cleanUp() {
            unmockkStatic(Tracing::class)
        }
    }
}

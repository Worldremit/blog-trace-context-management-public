package com.worldremit.base.tracing.brave

import brave.Tracing
import brave.propagation.CurrentTraceContext
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

internal class CurrentTraceContextFunctionTest {
    @Test
    fun `return null when current Tracing is null`() {
        mockkStatic(Tracing::class)
        every { Tracing.current() } returns null

        currentTraceContext().shouldBeNull()
    }

    @Test
    fun `return null when currentTracingContext is null`() {
        mockkStatic(Tracing::class)
        val tracing: Tracing = mockk()
        every { Tracing.current() } returns tracing
        every { tracing.currentTraceContext() } returns null

        currentTraceContext().shouldBeNull()
    }

    @Test
    fun `return currentTraceContext`() {
        mockkStatic(Tracing::class)
        val tracing: Tracing = mockk()
        val currentTraceContext: CurrentTraceContext = mockk()
        every { Tracing.current() } returns tracing
        every { tracing.currentTraceContext() } returns currentTraceContext

        currentTraceContext() shouldBe currentTraceContext
    }

    companion object {

        @JvmStatic
        @AfterAll
        fun cleanUp() {
            unmockkStatic(Tracing::class)
        }
    }
}

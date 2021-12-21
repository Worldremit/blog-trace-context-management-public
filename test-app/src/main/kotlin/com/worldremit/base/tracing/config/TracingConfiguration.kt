package com.worldremit.base.tracing.config

import arrow.core.curried
import arrow.core.partially1
import brave.Tracing
import com.worldremit.base.tracing.CaptureTracingContext
import com.worldremit.base.tracing.CreateTracingCoroutineContext
import com.worldremit.base.tracing.brave.CreateTraceContext
import com.worldremit.base.tracing.brave.createBraveTracingCoroutineContext
import com.worldremit.base.tracing.brave.captureBraveTracingContext
import com.worldremit.base.tracing.brave.createBraveTraceContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TracingConfiguration {

    @Bean
    fun createBraveTraceContextFun(): CreateTraceContext = ::createBraveTraceContext

    @Bean
    fun captureTracingContextFun(tracing: Tracing): CaptureTracingContext =
        ::captureBraveTracingContext.partially1(tracing)

    @Bean
    fun createTracingCoroutineContext(
        createTraceContext: CreateTraceContext,
        tracing: Tracing
    ): CreateTracingCoroutineContext =
        ::createBraveTracingCoroutineContext.curried()(createTraceContext)(tracing)
}

package com.worldremit.base.tracing.brave

import brave.Tracing
import brave.propagation.ThreadLocalCurrentTraceContext
import brave.propagation.TraceContext
import com.worldremit.base.tracing.TracingContext
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.coroutines.EmptyCoroutineContext

internal class BraveTracingTest : DescribeSpec({
    val tracingContext = TracingContext(
        mapOf(
            "X-B3-TraceId" to "0000000000000001",
            "X-B3-SpanId" to "0000000000000002",
            "X-B3-ParentSpanId" to "0000000000000003",
            "X-B3-Sampled" to "1"
        )
    )
    val traceContext = TraceContext.newBuilder()
        .traceId(1)
        .spanId(2)
        .parentId(3)
        .sampled(true).build()

    lateinit var tracing: Tracing
    beforeTest {
        tracing = Tracing.newBuilder()
            .currentTraceContext(ThreadLocalCurrentTraceContext.create())
            .build()
    }
    afterTest {
        tracing.close()
    }

    describe("captureBraveTracingContext") {
        context("no trace context") {
            it("captures empty context") {
                captureBraveTracingContext(tracing) shouldBe TracingContext(emptyMap())
            }
        }
        context("trace context present") {
            beforeTest {
                tracing.currentTraceContext().newScope(traceContext)
            }
            it("captures context") {
                captureBraveTracingContext(tracing) shouldBe tracingContext
            }
        }
    }

    describe("braveTracingCoroutineContext") {
        context("empty captured tracing context") {
            it("returns empty context") {
                val context = createBraveTracingCoroutineContext(
                    ::createBraveTraceContext,
                    tracing,
                    TracingContext(emptyMap())
                )
                context shouldBe EmptyCoroutineContext
            }
        }
        context("non-empty captured tracing context") {
            it("returns context with expected trace context") {
                val coroutineContext = createBraveTracingCoroutineContext(
                    ::createBraveTraceContext,
                    tracing,
                    tracingContext
                )
                coroutineContext.shouldBeInstanceOf<TraceContextElement>()
                coroutineContext.context shouldBe traceContext
            }
        }
    }
})

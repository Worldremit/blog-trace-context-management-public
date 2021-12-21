package com.worldremit.base.outbox.config

import arrow.core.curried
import com.worldremit.base.outbox.AcquireEntry
import com.worldremit.base.outbox.DeliverMessage
import com.worldremit.base.outbox.DeliverToTopic
import com.worldremit.base.outbox.ReleaseEntry
import com.worldremit.base.outbox.RemoveEntry
import com.worldremit.base.outbox.SendMessage
import com.worldremit.base.outbox.StoreEntry
import com.worldremit.base.outbox.deliverMessage
import com.worldremit.base.outbox.randomDeliverToTopic
import com.worldremit.base.outbox.sendMessage
import com.worldremit.base.tracing.CaptureTracingContext
import com.worldremit.base.tracing.CreateTracingCoroutineContext
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.TimeUnit.SECONDS

@Configuration
class OutboxConfiguration {
    private val logger = KotlinLogging.logger {}

    @Bean
    fun sendMessageFun(storeEntry: StoreEntry, captureTracingContext: CaptureTracingContext): SendMessage =
        ::sendMessage.curried()(storeEntry)(captureTracingContext)

    @Bean
    fun deliverToTopicFun(): DeliverToTopic = ::randomDeliverToTopic

    @Bean
    fun deliverMessageFun(
        acquireEntry: AcquireEntry,
        releaseEntry: ReleaseEntry,
        removeEntry: RemoveEntry,
        deliverToTopic: DeliverToTopic,
        createTracingCoroutineContext: CreateTracingCoroutineContext
    ): DeliverMessage = {
        deliverMessage(acquireEntry, releaseEntry, removeEntry, deliverToTopic, createTracingCoroutineContext)
    }
}

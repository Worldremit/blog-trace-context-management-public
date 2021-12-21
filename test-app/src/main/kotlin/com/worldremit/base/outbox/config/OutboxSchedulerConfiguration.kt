package com.worldremit.base.outbox.config

import com.worldremit.base.outbox.DeliverMessage
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.TimeUnit.SECONDS

@Configuration
class OutboxSchedulerConfiguration(private val deliverMessage: DeliverMessage) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(fixedDelay = 1, timeUnit = SECONDS)
    fun outboxDeliveryJob() = runBlocking {
//        logger.info { "Running scheduled message delivery..." }
        deliverMessage()
    }
}

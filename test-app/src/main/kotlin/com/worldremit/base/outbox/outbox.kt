package com.worldremit.base.outbox

import arrow.core.Either
import arrow.core.computations.either
import arrow.core.left
import arrow.core.right
import com.worldremit.base.tracing.CaptureTracingContext
import com.worldremit.base.tracing.CreateTracingCoroutineContext
import com.worldremit.base.tracing.TracingContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.util.UUID
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

private val logger = KotlinLogging.logger {}

data class Topic(val value: String)

data class Payload(val value: Any)

data class Message(val topic: Topic, val payload: Payload)

sealed class SendError {
    object Persistence : SendError()
    object Validation : SendError()
    object Unknown : SendError()
}

typealias SendMessage = suspend (Message) -> Either<SendError, Unit>

sealed class DeliveryError {
    object Persistence : DeliveryError()
    object Unknown : DeliveryError()
}

typealias DeliverMessage = suspend () -> Either<DeliveryError, Unit>

@OptIn(ExperimentalTime::class)
suspend fun sendMessage(
    storeEntry: StoreEntry,
    captureTracingContext: CaptureTracingContext,
    message: Message
): Either<SendError, Unit> {
    logger.info { "Storing message: $message" }
    delay(1)
    val context = captureTracingContext()
    val entry = MessageEntry(EntryId(UUID.randomUUID()), message, context.value)
    return storeEntry(entry)
        .map { }
        .mapLeft { SendError.Persistence }
}

typealias DeliverToTopic = suspend (Topic, Payload) -> Either<DeliveryError, Unit>

suspend fun deliverMessage(
    acquireEntry: AcquireEntry,
    releaseEntry: ReleaseEntry,
    removeEntry: RemoveEntry,
    deliverToTopic: DeliverToTopic,
    createTracingContext: CreateTracingCoroutineContext,
): Either<DeliveryError, Unit> = either {
    val entry: MessageEntry? = acquireEntry().mapLeft(::mapError).bind()
    if (entry != null) {
        logger.info { "Delivering entry: $entry" }
        withContext(createTracingContext(TracingContext(entry.tracingContext))) {
            val message = entry.message
            logger.info { "Delivering message: $message" }
            val deliverResult = deliverToTopic(message.topic, message.payload)
            deliverResult.fold(
                { releaseEntry(entry).mapLeft(::mapError).bind()},
                { removeEntry(entry).mapLeft(::mapError).bind() }
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
suspend fun randomDeliverToTopic(topic: Topic, payload: Payload): Either<DeliveryError, Unit> {
    val success = Random.nextBoolean()
    delay(1.seconds)
    return if (success) {
        logger.info { "Deliver to topic: $topic, payload: $payload succeeded" }
        Unit.right()
    } else {
        logger.info { "Deliver to topic: $topic, payload: $payload failed" }
        DeliveryError.Unknown.left()
    }
}

fun mapError(error: StoreError): DeliveryError = DeliveryError.Persistence

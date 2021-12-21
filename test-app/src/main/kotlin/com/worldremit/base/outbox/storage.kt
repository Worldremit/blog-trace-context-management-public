package com.worldremit.base.outbox

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor

data class EntryId(val value: UUID)

data class MessageEntry(val id: EntryId, val message: Message, val tracingContext: Map<String, String>)

sealed class StoreError {
    object Unknown : StoreError()
}

object EntryStored

typealias StoreEntry = suspend (MessageEntry) -> Either<StoreError, EntryStored>

typealias AcquireEntry = suspend () -> Either<StoreError, MessageEntry?>

object EntryReleased

typealias ReleaseEntry = suspend (MessageEntry) -> Either<StoreError, EntryReleased>

object EntryRemoved

typealias RemoveEntry = suspend (MessageEntry) -> Either<StoreError, EntryRemoved>

class InMemoryEntryStore {

    private val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

    private val logger = KotlinLogging.logger {}

    private val entries = mutableListOf<MessageEntry>()

    suspend fun store(entry: MessageEntry): Either<StoreError, EntryStored> =
        withContext(dispatcher) {
            logger.info { "Storing: $entry" }
            entries.add(entry)
            EntryStored.right()
        }


    suspend fun acquire(): Either<StoreError, MessageEntry?> =
        withContext(dispatcher) {
            entries.firstOrNull().right()
                .also { result ->
                    if (result.fold({ false }, { entry -> entry != null })) {
                        logger.info { "Acquire result: $result" }
                    }
                }
        }

    suspend fun release(entry: MessageEntry): Either<StoreError, EntryReleased> =
        withContext(dispatcher) {
            logger.info { "Released entry: $entry" }
            EntryReleased.right()
        }

    suspend fun remove(entry: MessageEntry): Either<StoreError, EntryRemoved> =
        withContext(dispatcher) {
            logger.info { "Removing entry: $entry" }
            val removed = entries.remove(entry)
            if (removed) {
                EntryRemoved.right()
            } else {
                StoreError.Unknown.left()
            }
        }
}

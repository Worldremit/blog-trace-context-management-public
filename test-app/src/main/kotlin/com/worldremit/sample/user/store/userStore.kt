package com.worldremit.sample.user.store

import arrow.core.Either
import arrow.core.right
import com.worldremit.sample.user.domain.User
import kotlinx.coroutines.delay
import mu.KotlinLogging
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

private val logger = KotlinLogging.logger {}

sealed class StoreError {
    object Unknown: StoreError()
}

object UserStored

typealias StoreUser = suspend (User) -> Either<StoreError, UserStored>

@OptIn(ExperimentalTime::class)
suspend fun noopStoreUser(user: User): Either<StoreError, UserStored> {
    delay(1.seconds)
    logger.info { "User stored: $user" }
    return UserStored.right()
}

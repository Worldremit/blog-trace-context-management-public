package com.worldremit.sample.user.usecase

import arrow.core.Either
import arrow.core.computations.either
import com.worldremit.base.outbox.Message
import com.worldremit.base.outbox.Payload
import com.worldremit.base.outbox.SendError
import com.worldremit.base.outbox.SendMessage
import com.worldremit.base.outbox.Topic
import com.worldremit.sample.user.domain.Login
import com.worldremit.sample.user.domain.Name
import com.worldremit.sample.user.domain.User
import com.worldremit.sample.user.domain.UserId
import com.worldremit.sample.user.store.StoreError
import com.worldremit.sample.user.store.StoreUser
import java.time.Instant
import kotlin.time.ExperimentalTime

sealed class UseCaseError {
    object Unknown : UseCaseError()
}

object UserCreated {
    override fun toString(): String = "UserCreated"

}

data class CreateUserCommand(
    val id: UserId,
    val login: Login,
    val name: Name,
)

typealias CreateUser = suspend (CreateUserCommand) -> Either<UseCaseError, UserCreated>

data class UserCreatedEvent(
    val timestamp: Instant,
    val user: User
)

@OptIn(ExperimentalTime::class)
suspend fun createUser(
    storeUser: StoreUser,
    sendUserCreatedEvent: SendUserCreatedEvent,
    command: CreateUserCommand
): Either<UseCaseError, UserCreated> = either {
    val user = User(command.id, command.login, command.name)
    storeUser(user).mapLeft(::mapError).bind()
    sendUserCreatedEvent(user).mapLeft(::mapError).bind()
    UserCreated
}

fun mapError(error: StoreError): UseCaseError = UseCaseError.Unknown

fun mapError(error: SendError): UseCaseError = UseCaseError.Unknown

object UserCreatedEventSent

typealias SendUserCreatedEvent = suspend (User) -> Either<SendError, UserCreatedEventSent>

typealias Now = () -> Instant

suspend fun sendUserCreatedEvent(
    topic: Topic,
    sendMessage: SendMessage,
    now: Now,
    user: User
): Either<SendError, UserCreatedEventSent> {
    val event = UserCreatedEvent(now(), user)
    val message = Message(topic, Payload(event))
    return sendMessage(message)
        .map { UserCreatedEventSent }
}

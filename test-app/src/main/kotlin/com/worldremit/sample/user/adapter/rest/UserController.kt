package com.worldremit.sample.user.adapter.rest

import com.worldremit.base.tracing.brave.TraceContextElement
import com.worldremit.sample.user.domain.FirstName
import com.worldremit.sample.user.domain.LastName
import com.worldremit.sample.user.domain.Login
import com.worldremit.sample.user.domain.MiddleName
import com.worldremit.sample.user.domain.Name
import com.worldremit.sample.user.domain.UserId
import com.worldremit.sample.user.usecase.CreateUser
import com.worldremit.sample.user.usecase.CreateUserCommand
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class NameDto(
    val first: String,
    val middle: String?,
    val last: String,
)

data class UserDto(
    val login: String,
    val name: NameDto
)

@RestController
@RequestMapping("/users")
class UserController(private val createUser: CreateUser) {
    private val logger = KotlinLogging.logger {}

    @GetMapping
    suspend fun users(): ResponseEntity<String> {
        logger.info("Users")
        return ResponseEntity.ok("none")
    }

    @PutMapping("/{id}")
    suspend fun sendMessage(@PathVariable id: String, @RequestBody dto: UserDto): ResponseEntity<String> =
        withContext(MDCContext() + TraceContextElement()) {
            logger.info { "UserDto: $dto" }
            val command = CreateUserCommand(UserId(UUID.fromString(id)), Login(dto.login), name(dto.name))
            createUser(command).fold(
                { error -> ResponseEntity.badRequest().body("Something went wrong: $error") },
                { result -> ResponseEntity.ok().body(result.toString()) }
            )
        }

    private fun name(name: NameDto): Name =
        Name(FirstName(name.first), name.middle?.let { m -> MiddleName(m) }, LastName(name.last))
}


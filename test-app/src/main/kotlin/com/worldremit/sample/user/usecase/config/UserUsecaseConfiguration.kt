package com.worldremit.sample.user.usecase.config

import arrow.core.curried
import com.worldremit.base.outbox.SendMessage
import com.worldremit.base.outbox.Topic
import com.worldremit.sample.user.store.StoreUser
import com.worldremit.sample.user.usecase.CreateUser
import com.worldremit.sample.user.usecase.Now
import com.worldremit.sample.user.usecase.SendUserCreatedEvent
import com.worldremit.sample.user.usecase.createUser
import com.worldremit.sample.user.usecase.sendUserCreatedEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant

@Configuration
class UserUsecaseConfiguration {
    @Bean
    fun createUserFun(storeUser: StoreUser, sendUserCreatedEvent: SendUserCreatedEvent): CreateUser =
        ::createUser.curried()(storeUser)(sendUserCreatedEvent)

    @Bean
    fun sendUserCreatedEventFun(sendMessage: SendMessage, now: Now): SendUserCreatedEvent =
        ::sendUserCreatedEvent.curried()(Topic("user.events"))(sendMessage)(now)

    @Bean
    fun nowFun(): Now = Instant::now
}

package com.worldremit.sample.user.store.config

import com.worldremit.sample.user.store.StoreUser
import com.worldremit.sample.user.store.noopStoreUser
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UserStoreConfiguration {
    @Bean
    fun storeUserFun(): StoreUser = ::noopStoreUser
}

package com.worldremit.base.outbox.config

import com.worldremit.base.outbox.AcquireEntry
import com.worldremit.base.outbox.InMemoryEntryStore
import com.worldremit.base.outbox.ReleaseEntry
import com.worldremit.base.outbox.RemoveEntry
import com.worldremit.base.outbox.StoreEntry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class StorageConfiguration {

    @Bean
    fun inMemoryEntryStore(): InMemoryEntryStore = InMemoryEntryStore()

    @Bean
    fun storeEntryFun(store: InMemoryEntryStore): StoreEntry = store::store

    @Bean
    fun acquireEntryFun(store: InMemoryEntryStore): AcquireEntry = store::acquire

    @Bean
    fun releaseEntryFun(store: InMemoryEntryStore): ReleaseEntry = store::release

    @Bean
    fun removeEntryFun(store: InMemoryEntryStore): RemoveEntry = store::remove
}

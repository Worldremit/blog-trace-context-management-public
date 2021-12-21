package com.worldremit.sample

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication(scanBasePackages = ["com.worldremit"])
class SampleApplication

fun main(args: Array<String>) {
    SpringApplication.run(SampleApplication::class.java, *args)
}

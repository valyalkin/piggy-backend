package com.valyalkin.piggy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PiggyBackendApplication

fun main(args: Array<String>) {
    runApplication<PiggyBackendApplication>(*args)
}

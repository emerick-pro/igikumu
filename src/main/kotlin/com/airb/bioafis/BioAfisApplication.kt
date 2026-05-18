package com.airb.bioafis

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
class BioAfisApplication

fun main(args: Array<String>) {
    runApplication<BioAfisApplication>(*args)
}

package kz.mybrain.superkassa.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class SuperkassaApplication

fun main(args: Array<String>) {
    org.springframework.boot.SpringApplication.run(arrayOf(SuperkassaApplication::class.java), args)
}

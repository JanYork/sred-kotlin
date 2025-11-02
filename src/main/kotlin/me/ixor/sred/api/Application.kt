package me.ixor.sred.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

/**
 * Spring Boot 应用主函数
 */
@SpringBootApplication
@ComponentScan(basePackages = ["me.ixor.sred"])
open class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

package com.github.psyoung16.delivery

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PassThroughDeliveryApplication

fun main(args: Array<String>) {
	runApplication<PassThroughDeliveryApplication>(*args)
}
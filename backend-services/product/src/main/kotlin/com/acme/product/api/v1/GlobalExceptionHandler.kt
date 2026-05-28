package com.acme.product.api.v1

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to (ex.message ?: "Invalid request")))
    }
}

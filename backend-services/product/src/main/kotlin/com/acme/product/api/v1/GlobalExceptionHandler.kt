package com.acme.product.api.v1

import com.acme.product.domain.ProductNotFoundException
import com.acme.product.domain.VariantNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import tools.jackson.core.JacksonException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to (ex.message ?: "Invalid request")))
    }

    @ExceptionHandler(ProductNotFoundException::class)
    fun handleProductNotFound(ex: ProductNotFoundException): ResponseEntity<Map<String, String>> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(mapOf("error" to (ex.message ?: "Product not found")))
    }

    @ExceptionHandler(VariantNotFoundException::class)
    fun handleVariantNotFound(ex: VariantNotFoundException): ResponseEntity<Map<String, String>> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(mapOf("error" to (ex.message ?: "Variant not found")))
    }

    /** A body that fails `@Valid`, e.g. `"pageSize": 101` (PIN-303). */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleInvalidRequest(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, String>> =
        invalidRequest(ex.bindingResult.fieldErrors.map { it.field }.minOrNull(), ex)

    /**
     * A body Jackson can't read, e.g. `"pageSize": 24.9` or malformed JSON (PIN-303). Names the
     * field from Jackson's path, never its message, which has class names and parser internals.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableRequest(ex: HttpMessageNotReadableException): ResponseEntity<Map<String, String>> {
        val jackson = generateSequence<Throwable>(ex) { it.cause }.filterIsInstance<JacksonException>().firstOrNull()
        return invalidRequest(jackson?.path?.let(::fieldPath), ex)
    }

    private fun invalidRequest(field: String?, cause: Exception): ResponseEntity<Map<String, String>> {
        logger.debug("Rejected an invalid request: {}", cause.message)
        val message = if (field == null) "Invalid request" else "Invalid request: $field"
        return ResponseEntity.badRequest().body(mapOf("error" to message, "code" to INVALID_REQUEST))
    }

    /** `filters.priceMin`, `items[0].sku`; null for an empty path, e.g. malformed JSON. */
    private fun fieldPath(path: List<JacksonException.Reference>): String? =
        path.joinToString("") { ref -> ref.propertyName?.let { ".$it" } ?: "[${ref.index}]" }
            .removePrefix(".")
            .ifEmpty { null }

    companion object {
        /** The error code for a 400 from a request body that is invalid or unreadable (PIN-303). */
        const val INVALID_REQUEST = "INVALID_REQUEST"
    }
}

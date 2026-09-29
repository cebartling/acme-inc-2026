package com.acme.product.api.v1

import com.acme.product.domain.ProductNotFoundException
import com.acme.product.domain.VariantNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import tools.jackson.core.JacksonException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /** Also a search body's comma in a category name, rejected by `SearchFilters` (PIN-303). */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to (ex.message ?: "Invalid request"), "code" to INVALID_REQUEST))
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
        invalidRequest(ex.bindingResult.fieldErrors.minOfOrNull { it.field }, ex)

    /**
     * A body Jackson can't read, e.g. `"pageSize": 24.9` or malformed JSON (PIN-303). Names the
     * field from Jackson's path, never its message, which has class names and parser internals.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableRequest(ex: HttpMessageNotReadableException): ResponseEntity<Map<String, String>> {
        val jackson = generateSequence<Throwable>(ex) { it.cause }.filterIsInstance<JacksonException>().firstOrNull()
        return invalidRequest(jackson?.path?.let(::fieldPath), ex)
    }

    /**
     * A path or query parameter of the wrong type, e.g. `?limit=abc` or a variant ID that is no
     * UUID (PIN-305). Handled here so it doesn't reach [handleIllegalArgument] through its
     * cause, whose message is the parser's (`For input string: "abc"`).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleParameterTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<Map<String, String>> =
        invalidRequest(ex.name, ex)

    /** A query parameter that fails its constraint, e.g. `?q=a` or `?limit=21` (PIN-305). */
    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleInvalidParameter(ex: HandlerMethodValidationException): ResponseEntity<Map<String, String>> =
        invalidRequest(ex.parameterValidationResults.mapNotNull { requestParamName(it.methodParameter) }.minOrNull(), ex)

    /** A required query parameter that is missing, e.g. autocomplete without `q` (PIN-305). */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(ex: MissingServletRequestParameterException): ResponseEntity<Map<String, String>> =
        invalidRequest(ex.parameterName, ex)

    private fun invalidRequest(field: String?, cause: Exception): ResponseEntity<Map<String, String>> {
        logger.debug("Rejected an invalid request: {}", cause.message)
        val message = if (field == null) "Invalid request" else "Invalid request: $field"
        return ResponseEntity.badRequest().body(mapOf("error" to message, "code" to INVALID_REQUEST))
    }

    /** The name the client sent, e.g. `q` for `@RequestParam("q") query`; the Kotlin name otherwise. */
    private fun requestParamName(parameter: MethodParameter): String? {
        val requestParam = parameter.getParameterAnnotation(RequestParam::class.java)
        return requestParam?.value?.ifEmpty { requestParam.name }?.ifEmpty { null } ?: parameter.parameterName
    }

    /**
     * `filters.priceMin`, `items[0].sku`; null for an empty path, e.g. malformed JSON at the top
     * level. A syntax error inside an object names that object, e.g. `filters`.
     */
    private fun fieldPath(path: List<JacksonException.Reference>): String? =
        path.joinToString("") { ref -> ref.propertyName?.let { ".$it" } ?: "[${ref.index}]" }
            .removePrefix(".")
            .ifEmpty { null }

    companion object {
        /** The error code for a 400 from an invalid or unreadable request (PIN-303). */
        const val INVALID_REQUEST = "INVALID_REQUEST"
    }
}

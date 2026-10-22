package com.acme.order.api.v1

import com.acme.order.application.PlaceOrderCommand
import com.acme.order.application.PlaceOrderResult
import com.acme.order.application.PlaceOrderUseCase
import com.acme.order.domain.Order
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PlaceOrderRequest(
    @field:NotNull
    val checkoutSessionId: UUID?
)

data class MoneyResponse(val amount: BigDecimal, val currency: String)

data class OrderSummaryResponse(val subtotal: MoneyResponse)

/** Journey 0005 step 5's response, cut down to what exists: a subtotal-only summary. */
data class PlaceOrderResponse(
    val orderId: UUID,
    val orderNumber: String,
    val status: String,
    val createdAt: Instant,
    val summary: OrderSummaryResponse
) {
    companion object {
        fun from(order: Order) = PlaceOrderResponse(
            orderId = order.id,
            orderNumber = order.orderNumber,
            status = order.status.name,
            createdAt = order.createdAt,
            summary = OrderSummaryResponse(MoneyResponse(order.subtotal, order.currency))
        )
    }
}

/**
 * Places orders (PIN-366). Signed-in customers only: the order service verifies identity's
 * `access_token` cookie and forwards it to the cart (ADR-0044). Errors are `{error, code}`
 * (ADR-0050).
 */
@RestController
@RequestMapping("/api/v1/orders")
class OrderController(private val placeOrder: PlaceOrderUseCase) {

    private val logger = LoggerFactory.getLogger(OrderController::class.java)

    /**
     * `201` with the order, also when an earlier request with the same `Idempotency-Key`
     * placed it (AC-5.8). `503 CART_UNAVAILABLE` can be retried with the same key.
     */
    @PostMapping
    fun place(
        // SecurityConfig answers 401 before this runs when there's no verified token
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader(IDEMPOTENCY_KEY_HEADER, required = false) idempotencyKey: String?,
        @Valid @RequestBody request: PlaceOrderRequest
    ): ResponseEntity<Any> {
        val key = idempotencyKey?.trim()
        if (key.isNullOrEmpty() || key.length > MAX_IDEMPOTENCY_KEY_LENGTH) {
            return error(
                HttpStatus.BAD_REQUEST,
                "An $IDEMPOTENCY_KEY_HEADER header of 1 to $MAX_IDEMPOTENCY_KEY_LENGTH characters is required",
                IDEMPOTENCY_KEY_REQUIRED
            )
        }

        val command = PlaceOrderCommand(
            userId = UUID.fromString(jwt.subject),
            accessToken = jwt.tokenValue,
            checkoutSessionId = request.checkoutSessionId!!,
            idempotencyKey = key
        )
        return when (val result = placeOrder.execute(command)) {
            is PlaceOrderResult.Placed -> ResponseEntity.status(HttpStatus.CREATED).body(PlaceOrderResponse.from(result.order))
            is PlaceOrderResult.Refused -> error(result.status, result.message, result.code)
            PlaceOrderResult.CartUnavailable -> error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The order couldn't be completed right now. Please try again.",
                CART_UNAVAILABLE
            )
        }
    }

    /** A body that fails `@Valid`, e.g. no `checkoutSessionId`. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidRequest(invalid: MethodArgumentNotValidException): ResponseEntity<Any> =
        invalidRequestResponse(invalid.bindingResult.fieldErrors.minOfOrNull { it.field }, invalid)

    /** A body Jackson can't read, e.g. malformed JSON or a `checkoutSessionId` that is no UUID. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadableRequest(unreadable: HttpMessageNotReadableException): ResponseEntity<Any> =
        invalidRequestResponse(null, unreadable)

    private fun invalidRequestResponse(field: String?, cause: Exception): ResponseEntity<Any> {
        logger.debug("Rejected an invalid order request: {}", cause.message)
        val message = if (field == null) "Invalid request" else "Invalid request: $field"
        return error(HttpStatus.BAD_REQUEST, message, INVALID_REQUEST)
    }

    private fun error(status: HttpStatus, message: String, code: String): ResponseEntity<Any> =
        ResponseEntity.status(status).body(mapOf("error" to message, "code" to code))

    companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        const val MAX_IDEMPOTENCY_KEY_LENGTH = 255
        const val IDEMPOTENCY_KEY_REQUIRED = "IDEMPOTENCY_KEY_REQUIRED"
        const val INVALID_REQUEST = "INVALID_REQUEST"
        const val CART_UNAVAILABLE = "CART_UNAVAILABLE"
    }
}

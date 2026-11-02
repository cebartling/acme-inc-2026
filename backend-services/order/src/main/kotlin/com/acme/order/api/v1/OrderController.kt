package com.acme.order.api.v1

import com.acme.order.application.CancelOrderUseCase
import com.acme.order.application.FindOrderByKeyUseCase
import com.acme.order.application.GetOrderResult
import com.acme.order.application.GetOrderUseCase
import com.acme.order.application.ListOrdersUseCase
import com.acme.order.application.PlaceOrderCommand
import com.acme.order.application.PlaceOrderResult
import com.acme.order.application.PlaceOrderUseCase
import com.acme.order.domain.Order
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PlaceOrderRequest(
    @field:NotNull
    val checkoutSessionId: UUID?
)

data class CancelOrderRequest(
    @field:NotBlank
    @field:Size(max = OrderController.MAX_CANCEL_REASON_LENGTH)
    val reason: String?
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
 * Places orders (PIN-366), reads them back (PIN-367) and cancels them (PIN-429). Signed-in customers only: the order service verifies identity's
 * `access_token` cookie and forwards it to the cart (ADR-0044). Errors are `{error, code}`
 * (ADR-0050).
 */
@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val placeOrder: PlaceOrderUseCase,
    private val getOrder: GetOrderUseCase,
    private val listOrders: ListOrdersUseCase,
    private val findOrderByKey: FindOrderByKeyUseCase,
    private val cancelOrder: CancelOrderUseCase
) {

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
        val key = validKey(idempotencyKey) ?: return idempotencyKeyRequired()

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

    /**
     * The caller's order saved with this `Idempotency-Key`, PENDING or SUBMITTED (PIN-440), so a
     * client that lost an answer can ask without placing anything. The key is a header, as on
     * `POST`, which keeps it out of URLs and access logs. No such order, or a CANCELLED one, is
     * `404 ORDER_NOT_FOUND`.
     */
    @GetMapping("/by-idempotency-key")
    fun findByKey(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader(IDEMPOTENCY_KEY_HEADER, required = false) idempotencyKey: String?
    ): ResponseEntity<Any> {
        val key = validKey(idempotencyKey) ?: return idempotencyKeyRequired()
        return findOrderByKey.execute(UUID.fromString(jwt.subject), key)
            ?.let { ResponseEntity.ok<Any>(PlaceOrderResponse.from(it)) }
            ?: error(HttpStatus.NOT_FOUND, "Order not found", ORDER_NOT_FOUND)
    }

    /**
     * One of the caller's orders, SUBMITTED or cancelled by them (PIN-367, PIN-429). Missing,
     * someone else's, PENDING and failed placements all answer the same `404 ORDER_NOT_FOUND`.
     */
    @GetMapping("/{orderId}")
    fun get(@AuthenticationPrincipal jwt: Jwt, @PathVariable orderId: UUID): ResponseEntity<Any> =
        orderResponse(getOrder.execute(orderId, UUID.fromString(jwt.subject)))

    /**
     * Cancels the caller's SUBMITTED order (PIN-429). `200` with the order, also when they already
     * cancelled it. Any other order answers as `GET` does, `404 ORDER_NOT_FOUND`. The JSON body
     * is required, which `SecurityConfig`'s CSRF reasoning depends on.
     */
    @PostMapping("/{orderId}/cancel")
    fun cancel(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable orderId: UUID,
        @Valid @RequestBody request: CancelOrderRequest
    ): ResponseEntity<Any> =
        orderResponse(cancelOrder.execute(orderId, UUID.fromString(jwt.subject), request.reason!!))

    private fun orderResponse(result: GetOrderResult): ResponseEntity<Any> =
        when (result) {
            is GetOrderResult.Found -> ResponseEntity.ok(OrderDetailsResponse.from(result.order))
            GetOrderResult.NotFound -> error(HttpStatus.NOT_FOUND, "Order not found", ORDER_NOT_FOUND)
        }

    /**
     * The caller's SUBMITTED orders and those they cancelled, newest first (PIN-367, PIN-429).
     * `page` is 1-based and capped, so the offset can't overflow (the product search's PIN-308
     * bug).
     */
    @GetMapping
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "1") @Min(1) @Max(MAX_PAGE.toLong()) page: Int,
        @RequestParam(defaultValue = "24") @Min(1) @Max(MAX_PAGE_SIZE.toLong()) pageSize: Int
    ): ResponseEntity<Any> =
        ResponseEntity.ok(OrderListResponse.from(listOrders.execute(UUID.fromString(jwt.subject), page, pageSize)))

    /** A path ID that is no UUID, or a paging parameter that is no number. Names the parameter. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun invalidParameter(mismatch: MethodArgumentTypeMismatchException): ResponseEntity<Any> =
        invalidRequestResponse(mismatch.name, mismatch)

    /** A paging parameter out of range, e.g. `page=0` or `pageSize=101`. */
    @ExceptionHandler(HandlerMethodValidationException::class)
    fun invalidParameterValue(invalid: HandlerMethodValidationException): ResponseEntity<Any> =
        invalidRequestResponse(
            invalid.parameterValidationResults.mapNotNull { it.methodParameter.parameterName }.minOrNull(),
            invalid
        )

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

    private fun validKey(idempotencyKey: String?): String? =
        idempotencyKey?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_IDEMPOTENCY_KEY_LENGTH }

    private fun idempotencyKeyRequired(): ResponseEntity<Any> = error(
        HttpStatus.BAD_REQUEST,
        "An $IDEMPOTENCY_KEY_HEADER header of 1 to $MAX_IDEMPOTENCY_KEY_LENGTH characters is required",
        IDEMPOTENCY_KEY_REQUIRED
    )

    private fun error(status: HttpStatus, message: String, code: String): ResponseEntity<Any> =
        ResponseEntity.status(status).body(mapOf("error" to message, "code" to code))

    companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        const val MAX_IDEMPOTENCY_KEY_LENGTH = 255
        const val IDEMPOTENCY_KEY_REQUIRED = "IDEMPOTENCY_KEY_REQUIRED"
        const val INVALID_REQUEST = "INVALID_REQUEST"
        const val CART_UNAVAILABLE = "CART_UNAVAILABLE"
        const val ORDER_NOT_FOUND = "ORDER_NOT_FOUND"
        const val MAX_CANCEL_REASON_LENGTH = 500
        const val MAX_PAGE = 10_000
        const val MAX_PAGE_SIZE = 100
    }
}

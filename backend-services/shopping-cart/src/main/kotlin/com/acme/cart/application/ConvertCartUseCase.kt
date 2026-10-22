package com.acme.cart.application

import arrow.core.Either
import arrow.core.left
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartConversion
import com.acme.cart.domain.CartError
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.events.CartConvertedToOrder
import com.acme.cart.domain.events.CartConvertedToOrderPayload
import com.acme.cart.infrastructure.messaging.OutboxWriter
import com.acme.cart.infrastructure.persistence.CartRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ConvertCartCommand(val owner: CartOwner, val cartId: UUID, val orderId: UUID, val checkoutSessionId: UUID)

/**
 * Converts the caller's own cart into the order the order service placed for its checkout
 * session (PIN-331, journey 0005 step 5): the cart is CONVERTED, and `CartConvertedToOrder` is
 * written to the outbox in the same transaction (PIN-384), so it is sent even if Kafka is down
 * at the time. Converting again into the same order saves and writes nothing and
 * answers the same, so the order service can retry. The cart is found in any status, so that
 * repeat still finds it once it is no longer the owner's current cart.
 */
@Service
class ConvertCartUseCase(
    private val cartRepository: CartRepository,
    private val outboxWriter: OutboxWriter,
    private val transactionTemplate: TransactionTemplate
) {
    fun execute(command: ConvertCartCommand, correlationId: UUID = UUID.randomUUID()): Either<CartError, Converted> =
        retryOnConflict("Convert cart") { convertOnce(command, correlationId) }

    private fun convertOnce(command: ConvertCartCommand, correlationId: UUID): Either<CartError, Converted> {
        // Postgres keeps microseconds, so the first answer's convertedAt matches a repeat's, which is read back.
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val result = transactionTemplate.execute {
            val cart = cartRepository.findOwnedCartInAnyStatus(command.owner, command.cartId)
                ?: return@execute CartError.CartNotFound(command.cartId).left()
            cart.convertToOrder(command.orderId, command.checkoutSessionId, now).map { conversion ->
                if (conversion.repeated) {
                    Converted(cart, conversion)
                } else {
                    val saved = cartRepository.save(cart)
                    writeEvent(saved, conversion, correlationId)
                    Converted(saved, conversion)
                }
            }
        }
        return checkNotNull(result) { "transaction for cart ${command.cartId} returned no result" }
    }

    data class Converted(val cart: Cart, val conversion: CartConversion)

    private fun writeEvent(cart: Cart, conversion: CartConversion, correlationId: UUID) {
        outboxWriter.write(
            CartConvertedToOrder.create(
                CartConvertedToOrderPayload(
                    cartId = cart.id,
                    orderId = conversion.orderId,
                    checkoutSessionId = conversion.checkoutSessionId,
                    convertedAt = conversion.convertedAt,
                    lineCount = cart.items.size,
                    itemCount = cart.itemCount,
                    sessionId = cart.sessionId,
                    userId = cart.userId
                ),
                correlationId
            )
        )
    }
}

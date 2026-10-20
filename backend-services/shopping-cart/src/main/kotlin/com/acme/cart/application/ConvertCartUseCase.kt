package com.acme.cart.application

import arrow.core.Either
import arrow.core.left
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartConversion
import com.acme.cart.domain.CartError
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.events.CartConvertedToOrder
import com.acme.cart.domain.events.CartConvertedToOrderPayload
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class ConvertCartCommand(val owner: CartOwner, val cartId: UUID, val orderId: UUID, val checkoutSessionId: UUID)

/**
 * Converts the caller's own cart into the order the order service placed for its checkout
 * session (PIN-331, journey 0005 step 5): the cart is CONVERTED, and `CartConvertedToOrder` is
 * published after commit. Converting again into the same order saves and publishes nothing and
 * answers the same, so the order service can retry. The cart is found in any status, so that
 * repeat still finds it once it is no longer the owner's current cart.
 */
@Service
class ConvertCartUseCase(
    private val cartRepository: CartRepository,
    private val eventPublisher: CartEventPublisher,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(ConvertCartUseCase::class.java)

    fun execute(command: ConvertCartCommand, correlationId: UUID = UUID.randomUUID()): Either<CartError, Converted> =
        retryOnConflict("Convert cart") { convertOnce(command, correlationId) }

    private fun convertOnce(command: ConvertCartCommand, correlationId: UUID): Either<CartError, Converted> {
        // Postgres keeps microseconds, so the first answer's convertedAt matches a repeat's, which is read back.
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val result = transactionTemplate.execute {
            val cart = cartRepository.findOwnedCartInAnyStatus(command.owner, command.cartId)
                ?: return@execute CartError.CartNotFound(command.cartId).left()
            cart.convertToOrder(command.orderId, command.checkoutSessionId, now).map { conversion ->
                Converted(if (conversion.repeated) cart else cartRepository.save(cart), conversion)
            }
        }
        return checkNotNull(result) { "transaction for cart ${command.cartId} returned no result" }
            .onRight { (cart, conversion) -> if (!conversion.repeated) publish(cart, conversion, correlationId) }
    }

    data class Converted(val cart: Cart, val conversion: CartConversion)

    private fun publish(cart: Cart, conversion: CartConversion, correlationId: UUID) {
        eventPublisher.publishLoggingFailure(
            CartConvertedToOrder.create(
                CartConvertedToOrderPayload(
                    cartId = cart.id,
                    orderId = conversion.orderId,
                    checkoutSessionId = conversion.checkoutSessionId,
                    lineCount = cart.items.size,
                    itemCount = cart.itemCount,
                    sessionId = cart.sessionId,
                    userId = cart.userId
                ),
                correlationId
            ),
            logger
        )
    }
}

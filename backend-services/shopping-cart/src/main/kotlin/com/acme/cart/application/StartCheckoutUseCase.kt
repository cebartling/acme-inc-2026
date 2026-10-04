package com.acme.cart.application

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.CheckoutSession
import com.acme.cart.domain.ProductSnapshot
import com.acme.cart.domain.UnavailableLine
import com.acme.cart.domain.events.CheckoutInitiated
import com.acme.cart.domain.events.CheckoutInitiatedPayload
import com.acme.cart.domain.events.Money
import com.acme.cart.infrastructure.messaging.CartEventPublisher
import com.acme.cart.infrastructure.persistence.CartRepository
import com.acme.cart.infrastructure.product.ProductAvailabilityClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class StartCheckoutCommand(val owner: CartOwner, val cartId: UUID)

/** The cart locked for checkout, and its checkout session. */
data class CheckoutStarted(val cart: Cart, val session: CheckoutSession)

/**
 * Starts checkout on the caller's own cart (PIN-329, journey 0005 step 1).
 *
 * An empty cart is refused. Every line's variant is checked with the product service first,
 * outside the transaction, as add-to-cart does for pricing; any line that can't be ordered
 * refuses checkout, listed with why. Otherwise the cart is re-read and locked as CHECKOUT in
 * a transaction, and `CheckoutInitiated` is published after commit. If the cart changed in
 * between (its version moved), that is a conflict and the retry checks it again.
 *
 * A cart already in checkout resumes its session (PIN-330): the expiry moves out, but nothing
 * is re-checked or published. A session that has lapsed but is not unlocked yet is treated as
 * no session: the cart is checked again and gets a new one, and the lapsed session is reported
 * as expired before `CheckoutInitiated`, since the unlock job will no longer find it.
 */
@Service
class StartCheckoutUseCase(
    private val cartRepository: CartRepository,
    private val availabilityClient: ProductAvailabilityClient,
    private val eventPublisher: CartEventPublisher,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: ObjectMapper,
    private val checkoutSessionExpiry: CheckoutSessionExpiry
) {
    private val logger = LoggerFactory.getLogger(StartCheckoutUseCase::class.java)

    fun execute(command: StartCheckoutCommand, correlationId: UUID = UUID.randomUUID()): Either<CartError, CheckoutStarted> =
        retryOnConflict("Start checkout") { startOnce(command, correlationId) }

    private fun startOnce(command: StartCheckoutCommand, correlationId: UUID): Either<CartError, CheckoutStarted> = either {
        val now = Instant.now()
        val cart = cartRepository.findOwnedCart(command.owner, command.cartId)
            ?: raise(CartError.CartNotFound(command.cartId))
        if (!cart.isInLiveCheckout(now)) {
            if (cart.items.isEmpty()) raise(CartError.CartEmpty(cart.id))
            val unavailable = unavailableLines(cart).bind()
            if (unavailable.isNotEmpty()) raise(CartError.CartUnavailableItems(unavailable))
        }

        val locked = checkNotNull(transactionTemplate.execute { lockInTransaction(command, checkedVersion = cart.version, now) }) {
            "checkout transaction for cart ${command.cartId} returned no result"
        }.bind()
        locked.lapsed?.let { checkoutSessionExpiry.report(locked.started.cart, it, correlationId) }
        if (locked.isNew) publish(locked.started, correlationId)
        locked.started
    }

    /** Each distinct variant is checked once; a lookup failure refuses checkout at once. */
    private fun unavailableLines(cart: Cart): Either<CartError, List<UnavailableLine>> = either {
        val issues = cart.items.map { it.variantId }.distinct()
            .associateWith { availabilityClient.issueWith(it).bind() }
        cart.items.mapNotNull { item ->
            issues[item.variantId]?.let { issue ->
                UnavailableLine(item.id, item.variantId, productNameOf(item.productSnapshot), issue)
            }
        }
    }

    /**
     * The re-read is fresh (no open-in-view), so its version is the committed one and saving it
     * could never conflict. A change since [checkedVersion], whose lines were checked, is a
     * conflict instead: the retry checks the changed cart.
     */
    private fun lockInTransaction(command: StartCheckoutCommand, checkedVersion: Long?, now: Instant): Either<CartError, Locked> {
        val cart = cartRepository.findOwnedCart(command.owner, command.cartId)
            ?: return CartError.CartNotFound(command.cartId).left()
        if (cart.version != checkedVersion) throw ObjectOptimisticLockingFailureException(Cart::class.java, cart.id)
        val resumed = cart.isInLiveCheckout(now)
        val lapsed = cart.lapsedCheckoutSession(now)
        return cart.startCheckout(SESSION_LENGTH, now).map { session ->
            Locked(CheckoutStarted(cartRepository.save(cart), session), isNew = !resumed, lapsed = lapsed)
        }
    }

    /** [lapsed] is the session this start replaced because it had lapsed, if any. */
    private data class Locked(val started: CheckoutStarted, val isNew: Boolean, val lapsed: CheckoutSession?)

    private fun productNameOf(snapshot: String): String =
        objectMapper.readValue(snapshot, ProductSnapshot::class.java).name

    private fun publish(started: CheckoutStarted, correlationId: UUID) {
        val cart = started.cart
        eventPublisher.publishLoggingFailure(
            CheckoutInitiated.create(
                CheckoutInitiatedPayload(
                    cartId = cart.id,
                    checkoutSessionId = started.session.id,
                    expiresAt = started.session.expiresAt,
                    itemCount = cart.itemCount,
                    subtotal = Money(cart.subtotal, AddItemToCartUseCase.CURRENCY),
                    sessionId = cart.sessionId,
                    userId = cart.userId
                ),
                correlationId
            ),
            logger
        )
    }

    companion object {
        /** A checkout session lapses after this long without activity (journey 0005 AC-1.3, PIN-330). */
        val SESSION_LENGTH: Duration = Duration.ofMinutes(30)
    }
}

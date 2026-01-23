package com.acme.identity.infrastructure.security

import java.security.PrivateKey
import java.security.PublicKey

/**
 * Represents an RSA key pair used for JWT signing.
 *
 * @property keyId The unique identifier for this key (kid in JWT header).
 * @property privateKey The RSA private key for signing tokens.
 * @property publicKey The RSA public key for verifying tokens.
 * @property algorithm The signing algorithm (RS256, RS384, RS512).
 */
data class SigningKey(
    val keyId: String,
    val privateKey: PrivateKey,
    val publicKey: PublicKey,
    val algorithm: String = "RS256"
)

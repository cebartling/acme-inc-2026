package com.acme.identity.api

import com.acme.identity.infrastructure.security.SigningKeyProvider
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.security.interfaces.RSAPublicKey

/**
 * Publishes the public halves of identity's signing keys as a JSON Web Key Set, so other
 * services can verify access tokens locally (US-0004-08: the shopping cart service needs
 * to know which customer is calling).
 *
 * Keys are held in memory by [SigningKeyProvider] and regenerated on restart, so verifiers
 * must re-fetch this set when they meet an unknown `kid` rather than caching it forever.
 */
@RestController
class JwksController(private val keyProvider: SigningKeyProvider) {

    @GetMapping("/.well-known/jwks.json")
    fun jwks(): Map<String, Any> {
        val keys = keyProvider.getVerificationKeys().map { key ->
            RSAKey.Builder(key.publicKey as RSAPublicKey)
                .keyID(key.keyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.parse(key.algorithm))
                .build()
        }
        // toJSONObject() emits public parameters only; private keys never leave this service.
        return JWKSet(keys).toJSONObject()
    }
}

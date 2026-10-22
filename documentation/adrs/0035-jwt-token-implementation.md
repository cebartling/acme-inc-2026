# ADR-0035: JWT Token Implementation with RS256

## Status

Accepted

**Current state (2026-10-21, PIN-386):** Key management works differently from what this ADR first said, and the Key Management bullets below have been corrected. `SigningKeyProvider` keeps its RSA keys only in memory, so every identity restart generates a new key and invalidates every token issued before it. A scheduled job runs daily at 02:00 and rotates the key once `identity.jwt.key-rotation-period-days` (30) have passed since the last rotation or since startup. It is not a fixed monthly schedule. Kids look like `key-YYYY-MM-xxxxxxxx`, with a random 8-hex suffix so two rotations in one month never collide. The last 3 rotated-out keys are kept for verification, and all retained keys are published at `/.well-known/jwks.json`. Vault integration is still not built (ADR-0009). [ADR-0043](0043-cross-service-jwt-verification.md) covers how other services verify these tokens: JWKS, cookie-borne tokens and key rotation as built.

## Context

After successful authentication (including MFA), the Identity Service needs to issue security tokens that allow customers to access protected resources across the ACME platform. These tokens must be:

1. **Secure**: Prevent tampering and ensure authenticity
2. **Stateless**: Enable horizontal scaling without server-side session lookups
3. **Short-lived**: Minimize the impact of token compromise
4. **Rotatable**: Support key rotation without invalidating existing tokens
5. **Standards-compliant**: Use industry-standard formats

Additionally, we need to support:
- Access tokens for short-term API access (15 minutes)
- Refresh tokens for obtaining new access tokens (7 days)
- Token revocation through session invalidation
- Protection against CSRF and XSS attacks

## Decision

We will implement JWT (JSON Web Token) authentication using the following approach:

### 1. Token Types and Structure

**Access Token (15-minute expiry):**
```json
{
  "header": {
    "alg": "RS256",
    "typ": "JWT",
    "kid": "key-2026-01-a1b2c3d4"
  },
  "payload": {
    "sub": "user-uuid",
    "email": "customer@example.com",
    "roles": ["CUSTOMER"],
    "sessionId": "sess_uuid",
    "iat": 1705487400,
    "exp": 1705488300,
    "iss": "https://auth.acme.com",
    "aud": "https://api.acme.com"
  }
}
```

**Refresh Token (7-day expiry):**
```json
{
  "header": {
    "alg": "RS256",
    "typ": "JWT",
    "kid": "key-2026-01-a1b2c3d4"
  },
  "payload": {
    "sub": "user-uuid",
    "sessionId": "sess_uuid",
    "tokenFamily": "fam_uuid",
    "iat": 1705487400,
    "exp": 1706092200,
    "iss": "https://auth.acme.com"
  }
}
```

### 2. Signing Algorithm

- **Algorithm**: RS256 (RSA Signature with SHA-256)
- **Key Size**: 2048-bit RSA keys
- **Rationale**:
  - Asymmetric cryptography allows public key verification without exposing private keys
  - Services can verify tokens using only the public key
  - Industry standard with broad library support
  - Better security than HMAC-based signatures for distributed systems

### 3. Key Management

- **Current Implementation**: In-memory RSA key pair generation
- **Key Rotation**: Daily check at 02:00 rotates once `key-rotation-period-days` (default 30) have passed; the 3 most recent previous keys stay valid for verification; keys are regenerated on restart
- **Key ID (kid)**: Format `key-YYYY-MM-xxxxxxxx` (random 8-hex suffix) for tracking
- **Future Enhancement**: HashiCorp Vault integration for secure key storage

### 4. Token Delivery

Tokens are delivered via secure HttpOnly cookies:

```
Set-Cookie: access_token=<jwt>; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=900
Set-Cookie: refresh_token=<jwt>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth/refresh; Max-Age=604800
```

**Security Properties:**
- `HttpOnly`: Prevents JavaScript access (XSS protection)
- `Secure`: HTTPS-only transmission (MITM protection)
- `SameSite=Strict`: CSRF protection
- Path restriction for refresh token reduces exposure surface

### 5. Library Choice

**Selected**: Nimbus JOSE + JWT (com.nimbusds:nimbus-jose-jwt:10.10)

**Rationale:**
- Industry-standard Java library for JWT/JOSE
- Comprehensive RS256 support
- Active maintenance and security updates
- Type-safe API with builder patterns
- Excellent documentation and community support

### 6. Token Revocation

- Tokens are intrinsically stateless and cannot be revoked directly
- Revocation is achieved through session invalidation in Redis
- Services can check session validity for sensitive operations
- Short access token expiry (15 minutes) limits exposure window

## Consequences

### Positive

1. **Scalability**: Stateless tokens enable horizontal scaling without coordination
2. **Security**: RS256 provides strong cryptographic guarantees
3. **Interoperability**: Standard JWT format works with any JWT-compatible library
4. **Performance**: Token verification is fast (public key cryptography)
5. **Key Rotation**: Kid header enables seamless key rotation
6. **Cookie Security**: HttpOnly cookies prevent common web attacks

### Negative

1. **Token Size**: JWTs are larger than opaque tokens (200-400 bytes)
2. **Revocation Complexity**: Immediate revocation requires session checks
3. **Key Management**: Requires secure key storage (Vault integration planned)
4. **Clock Skew**: Requires synchronized server clocks for exp/iat validation

### Risks

1. **Key Compromise**: If private key is compromised, all tokens can be forged
   - **Mitigation**: Regular key rotation, Vault integration, monitoring
2. **Token Theft**: Stolen tokens can be used until expiry
   - **Mitigation**: Short expiry, secure cookies, session binding
3. **Replay Attacks**: Tokens can be replayed within validity window
   - **Mitigation**: Session validation, token family for refresh tokens

## Implementation Details

- **Service**: `TokenService` (application layer)
- **Key Provider**: `SigningKeyProvider` (infrastructure layer)
- **Cookie Builder**: `AuthCookieBuilder` (infrastructure layer)
- **Integration Point**: MfaController after successful MFA verification
- **Configuration**: `JwtConfig` with environment-based settings

## Future Enhancements

1. **Vault Integration**: Store private keys in HashiCorp Vault
2. **Token Introspection**: Endpoint for validating tokens
3. **Sliding Sessions**: Extend session on activity
4. **Device Binding**: Bind tokens to specific devices
5. **Custom Claims**: Add custom claims for feature flags, permissions

## References

- RFC 7519: JSON Web Token (JWT)
- RFC 7515: JSON Web Signature (JWS)
- RFC 7517: JSON Web Key (JWK)
- OWASP JWT Cheat Sheet
- Nimbus JOSE JWT Documentation

## Related Decisions

- ADR-0036: Session Management with Redis
- ADR-0031: Credential Validation Security
- ADR-0033: MFA TOTP Implementation

## Date

2026-01-22

# =============================================================================
# Application Policy
# Grants read access to application secrets
# =============================================================================

# Read access to database credentials
path "secret/data/database/*" {
  capabilities = ["read"]
}

# Read access to API keys
path "secret/data/api-keys/*" {
  capabilities = ["read"]
}

# Read access to service-specific secrets
path "secret/data/services/*" {
  capabilities = ["read"]
}

# Allow token renewal
path "auth/token/renew-self" {
  capabilities = ["update"]
}

# Allow looking up own token
path "auth/token/lookup-self" {
  capabilities = ["read"]
}

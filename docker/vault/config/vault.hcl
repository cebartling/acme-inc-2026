# =============================================================================
# HashiCorp Vault Configuration
# Development mode configuration for local development
# =============================================================================

# Note: In dev mode, Vault runs in-memory with a pre-configured root token
# This configuration is for reference when running in non-dev mode

ui = true

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = true
}

storage "file" {
  path = "/vault/data"
}

api_addr = "http://0.0.0.0:8200"

# Disable mlock for containerized environments
disable_mlock = true

# Logging
log_level = "info"
log_format = "json"

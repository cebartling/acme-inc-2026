# =============================================================================
# Admin Policy
# Full access for administrative operations
# =============================================================================

# Full access to all secrets
path "secret/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}

# Manage auth methods
path "auth/*" {
  capabilities = ["create", "read", "update", "delete", "list", "sudo"]
}

# Manage system configuration
path "sys/*" {
  capabilities = ["create", "read", "update", "delete", "list", "sudo"]
}

# Manage policies
path "sys/policies/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}

# List available auth methods
path "sys/auth" {
  capabilities = ["read", "list"]
}

# Health check endpoint
path "sys/health" {
  capabilities = ["read", "sudo"]
}

package com.acme.customer.infrastructure.persistence

import com.acme.customer.domain.CustomerPreferences
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Spring Data JPA repository for [CustomerPreferences] entities.
 *
 * Provides standard CRUD operations for customer preferences.
 * The primary key (customerId) matches the corresponding Customer entity.
 */
@Repository
interface CustomerPreferencesRepository : JpaRepository<CustomerPreferences, UUID>

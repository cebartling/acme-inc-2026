package com.acme.identity.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Redis configuration for session storage.
 *
 * Configures:
 * - Redis repository support for automatic CRUD operations
 * - RedisTemplate with proper serialization for manual operations
 * - JSON serialization for session data
 * - String serialization for keys
 *
 * Sessions are stored with automatic TTL-based expiration.
 */
@Configuration
@EnableRedisRepositories(basePackages = ["com.acme.identity.infrastructure.persistence"])
class RedisConfig {

    /**
     * Configures RedisTemplate for manual Redis operations.
     *
     * Uses:
     * - StringRedisSerializer for keys (human-readable in Redis)
     * - GenericJackson2JsonRedisSerializer for values (supports Kotlin data classes)
     *
     * @param connectionFactory The Redis connection factory (auto-configured by Spring Boot).
     * @return Configured RedisTemplate instance.
     */
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = connectionFactory

        // Use String serializer for keys
        val stringSerializer = StringRedisSerializer()
        template.keySerializer = stringSerializer
        template.hashKeySerializer = stringSerializer

        // Use JSON serializer for values
        val jsonSerializer = GenericJackson2JsonRedisSerializer()
        template.valueSerializer = jsonSerializer
        template.hashValueSerializer = jsonSerializer

        template.afterPropertiesSet()
        return template
    }
}

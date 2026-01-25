package com.acme.customer.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.scheduling.annotation.EnableAsync

/**
 * Redis configuration for customer profile caching.
 *
 * Configures:
 * - Redis repository support for automatic CRUD operations
 * - RedisTemplate with proper serialization for manual operations
 * - JSON serialization for customer profile data
 * - String serialization for keys
 * - Async support for activity tracking
 *
 * Customer profiles are cached with 5-minute TTL to improve performance.
 */
@Configuration
@EnableRedisRepositories(basePackages = ["com.acme.customer.infrastructure.persistence"])
@EnableAsync
@EnableAspectJAutoProxy
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

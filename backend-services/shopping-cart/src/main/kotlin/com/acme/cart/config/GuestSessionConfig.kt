package com.acme.cart.config

import com.acme.cart.api.v1.GuestSessionInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Every cart request keeps a guest's session alive (PIN-288). */
@Configuration
class GuestSessionConfig(private val guestSessionInterceptor: GuestSessionInterceptor) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(guestSessionInterceptor).addPathPatterns("/api/v1/carts/**")
    }
}

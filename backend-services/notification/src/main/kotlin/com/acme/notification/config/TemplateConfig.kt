package com.acme.notification.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver
import org.thymeleaf.templatemode.TemplateMode

/**
 * Configuration for Thymeleaf email templates.
 */
@Configuration
class TemplateConfig {

    /**
     * Creates the template resolver for email templates.
     */
    @Bean
    fun emailTemplateResolver(): SpringResourceTemplateResolver {
        return SpringResourceTemplateResolver().apply {
            prefix = "classpath:/templates/"
            suffix = ".html"
            templateMode = TemplateMode.HTML
            characterEncoding = "UTF-8"
            isCacheable = true
            order = 1
        }
    }

    /**
     * Creates the template engine for email rendering.
     */
    @Bean
    fun emailTemplateEngine(): SpringTemplateEngine {
        return SpringTemplateEngine().apply {
            setTemplateResolver(emailTemplateResolver())
            enableSpringELCompiler = true
        }
    }
}

package com.acme.identity.api.v1

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.assertTrue

/**
 * The test API is off unless `acme.test-api.enabled` is true (PIN-548). `TestController` used to be
 * gated on the `docker` profile alone, which the Containerfile turns on, so a container deploy
 * served it (now with a read-model endpoint that returns user data) behind a default key.
 */
class TestControllerGateTest {

    private val runner = ApplicationContextRunner()
        .withInitializer { it.environment.setActiveProfiles("docker") }
        .withUserConfiguration(TestController::class.java)

    @Test
    fun `the test controller is not registered when the test API is off`() {
        runner.run { context ->
            // Without the gate Spring would try to build it and fail on its missing dependencies
            assertTrue(context.startupFailure == null, "the controller should not be created: ${context.startupFailure}")
            assertTrue(context.getBeanNamesForType(TestController::class.java).isEmpty())
        }
    }

    @Test
    fun `the test controller is registered when the test API is on`() {
        runner.withPropertyValues("acme.test-api.enabled=true").run { context ->
            // It is created and fails here only because its dependencies are not in this bare context
            assertTrue(context.startupFailure != null, "the controller should have been created")
            assertTrue(context.startupFailure!!.toString().contains("testController"))
        }
    }
}

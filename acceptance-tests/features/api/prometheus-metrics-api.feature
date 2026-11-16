@api @metrics
Feature: Prometheus metrics
  Every backend service serves its metrics at /actuator/prometheus. Local Prometheus scrapes them,
  and on the AWS demo environment the ADOT collector does (PIN-537).

  Scenario Outline: The <service> service serves Prometheus metrics
    When I request the Prometheus endpoint of the "<service>" service
    Then the API should respond with status 200
    And the metrics include "jvm_memory_used_bytes"

    Examples:
      | service      |
      | identity     |
      | customer     |
      | notification |
      | product      |
      | cart         |
      | order        |

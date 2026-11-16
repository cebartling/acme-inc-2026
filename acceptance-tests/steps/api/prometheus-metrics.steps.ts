import { When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { ApiClient } from '../../support/api-client.js';

// --- PIN-537: every service serves Prometheus metrics ---------------------------------

function clientFor(world: CustomWorld, service: string): ApiClient {
  const clients: Record<string, ApiClient> = {
    identity: world.identityApiClient,
    customer: world.customerApiClient,
    notification: world.notificationApiClient,
    product: world.productApiClient,
    cart: world.cartApiClient,
    order: world.orderApiClient,
  };
  const client = clients[service];
  if (!client) {
    throw new Error(
      `No API client for service "${service}"; expected one of ${Object.keys(clients)}`
    );
  }
  return client;
}

When(
  'I request the Prometheus endpoint of the {string} service',
  async function (this: CustomWorld, service: string) {
    // The endpoint serves text, so the client's default Accept: application/json would get a 406
    const response = await clientFor(this, service).get<string>('/actuator/prometheus', {
      headers: { Accept: 'text/plain' },
    });
    this.setLastResponse(response);
  }
);

Then('the metrics include {string}', async function (this: CustomWorld, metric: string) {
  const body = this.getLastResponse<string>()?.data;
  expect(typeof body, 'the response body is text').toBe('string');
  expect(body).toContain(metric);
});

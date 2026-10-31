import { CustomWorld } from './world.js';

/**
 * A real signed access token for a user, which needn't exist in identity: many scenarios seed
 * customers directly, with made-up user IDs. Issued by identity's test API, so the customer
 * service verifies it as it would one from a sign-in. Kept per user for the scenario.
 */
export async function accessTokenFor(world: CustomWorld, userId: string): Promise<string> {
  const key = `accessToken:${userId}`;
  const cached = world.getTestData<string>(key);
  if (cached) return cached;

  const response = await world.identityApiClient.post<{ accessToken: string }>(
    `/api/v1/test/users/${userId}/access-token`
  );
  world.setTestData(key, response.data.accessToken);
  return response.data.accessToken;
}

/** A `Cookie` header value signing a request in as that user. */
export async function accessCookieFor(world: CustomWorld, userId: string): Promise<string> {
  return `access_token=${await accessTokenFor(world, userId)}`;
}

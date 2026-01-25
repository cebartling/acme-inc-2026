import { Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';

/**
 * Common API assertion step definitions.
 *
 * These steps are shared across all API feature files and provide
 * consistent assertions for HTTP responses.
 *
 * Supports two data storage patterns for backward compatibility:
 * 1. Direct: lastResponseStatus, lastResponseData
 * 2. Nested: lastResponse.status, lastResponse.data
 */

interface ApiResponse<T = unknown> {
  status: number;
  data: T;
}

interface ApiErrorResponse {
  error?: string;
  message?: string;
  errors?: string[];
}

/**
 * Gets the response status from either storage pattern.
 */
function getResponseStatus(world: CustomWorld): number | undefined {
  // Try direct pattern first
  const directStatus = world.getTestData<number>('lastResponseStatus');
  if (directStatus !== undefined) {
    return directStatus;
  }

  // Fall back to nested pattern
  const response = world.getTestData<ApiResponse>('lastResponse');
  return response?.status;
}

/**
 * Gets the response data from either storage pattern.
 */
function getResponseData<T>(world: CustomWorld): T | undefined {
  // Try direct pattern first
  const directData = world.getTestData<T>('lastResponseData');
  if (directData !== undefined) {
    return directData;
  }

  // Fall back to nested pattern
  const response = world.getTestData<ApiResponse<T>>('lastResponse');
  return response?.data;
}

/**
 * Asserts the HTTP status code of the last API response.
 */
Then(
  'the API should respond with status {int}',
  async function (this: CustomWorld, expectedStatus: number) {
    const actualStatus = getResponseStatus(this);
    expect(actualStatus).toBeDefined();
    expect(actualStatus).toBe(expectedStatus);
  }
);

/**
 * Asserts the error field in the response body.
 */
Then(
  'the response should contain error {string}',
  async function (this: CustomWorld, expectedError: string) {
    const data = getResponseData<ApiErrorResponse>(this);
    expect(data?.error).toBe(expectedError);
  }
);

/**
 * Asserts the message field in the response body.
 */
Then(
  'the response should contain message {string}',
  async function (this: CustomWorld, expectedMessage: string) {
    const data = getResponseData<ApiErrorResponse>(this);
    expect(data?.message).toBe(expectedMessage);
  }
);

/**
 * Asserts that the response contains a validation error for a specific field.
 */
Then(
  'the response should contain a validation error for {string}',
  async function (this: CustomWorld, field: string) {
    const data = getResponseData<ApiErrorResponse>(this);
    const hasFieldError =
      data?.errors?.some((err) => err.toLowerCase().includes(field.toLowerCase())) ||
      data?.error?.toLowerCase().includes(field.toLowerCase()) ||
      data?.message?.toLowerCase().includes(field.toLowerCase());

    expect(hasFieldError).toBe(true);
  }
);

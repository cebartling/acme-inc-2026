import { When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { CustomWorld } from '../../support/world.js';

const PASSWORD = 'SecureP@ss123';
const KAFKA_CONTAINER = process.env.KAFKA_CONTAINER ?? 'acme-kafka';
/**
 * Registration, identity's outbox, the consumer, the email send, notification's outbox, Debezium and
 * the projector, each within seconds; Kafka coming back takes longer.
 */
const CATCH_UP_TIMEOUT_MS = 60_000;
const POLL_INTERVAL_MS = 1_000;

interface NotificationStatus {
  notificationId: string;
  notificationType: string;
  recipientId: string;
  recipientEmail: string;
  status: string;
  providerMessageId: string | null;
}

interface RegisteredUser {
  userId: string;
  email: string;
}

function registeredUser(world: CustomWorld): RegisteredUser {
  const user = world.getTestData<RegisteredUser>('notificationReadModelUser');
  expect(user, 'register the user for the notification read model first').toBeDefined();
  return user!;
}

function delivery(world: CustomWorld): NotificationStatus {
  const found = world.getTestData<NotificationStatus>('notificationReadModelDelivery');
  expect(found, 'wait for the SENT verification email first').toBeDefined();
  return found!;
}

When('a user registers for the notification read model', async function (this: CustomWorld) {
  const email = `notif-read-model-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
  const response = await this.identityApiClient.post<{ userId: string }>('/api/v1/users/register', {
    email,
    password: PASSWORD,
    firstName: 'Nora',
    lastName: 'Notified',
    tosAccepted: true,
    tosAcceptedAt: new Date().toISOString(),
    marketingOptIn: false,
  });
  this.setTestData('lastResponse', response);
  this.setTestData('lastResponseStatus', response.status);
  this.setTestData('lastResponseData', response.data);
  if (response.status === 201) {
    this.setTestData<RegisteredUser>('notificationReadModelUser', {
      userId: response.data.userId,
      email,
    });
  }
});

Then(
  'the notification lookups should eventually show a SENT verification email',
  { timeout: CATCH_UP_TIMEOUT_MS + 10_000 },
  async function (this: CustomWorld) {
    const { email } = registeredUser(this);
    // Each GET is bounded by the ApiClient's 10s request timeout, inside Cucumber's step limit
    await expect
      .poll(
        async () => {
          const response = await this.notificationApiClient.get<NotificationStatus[]>(
            `/api/v1/notifications/by-email/${encodeURIComponent(email)}`
          );
          const sent = response.status === 200
            ? response.data.find((n) => n.notificationType === 'EMAIL_VERIFICATION' && n.status === 'SENT')
            : undefined;
          if (sent) this.setTestData('notificationReadModelDelivery', sent);
          return sent !== undefined;
        },
        { timeout: CATCH_UP_TIMEOUT_MS, intervals: [POLL_INTERVAL_MS] }
      )
      .toBe(true);
  }
);

Then(
  'looking the notification up by recipient id should find the same delivery',
  async function (this: CustomWorld) {
    const expected = delivery(this);
    const response = await this.notificationApiClient.get<NotificationStatus[]>(
      `/api/v1/notifications/recipient/${registeredUser(this).userId}`
    );
    expect(response.status).toBe(200);
    expect(response.data.find((n) => n.notificationId === expected.notificationId)).toEqual(expected);
  }
);

Then(
  'looking the notification up by its id should find the same delivery',
  async function (this: CustomWorld) {
    const expected = delivery(this);
    const response = await this.notificationApiClient.get<NotificationStatus>(
      `/api/v1/notifications/${expected.notificationId}/status`
    );
    expect(response.status).toBe(200);
    expect(response.data).toEqual(expected);
  }
);

/** Every `key<TAB>value` record on the topic, read from the beginning. */
function readTopic(topic: string): { key: string; value: string }[] {
  const output = execFileSync(
    'docker',
    [
      'exec',
      KAFKA_CONTAINER,
      'kafka-console-consumer',
      '--bootstrap-server',
      'localhost:9092',
      '--topic',
      topic,
      '--from-beginning',
      '--timeout-ms',
      '5000',
      '--property',
      'print.key=true',
    ],
    // The topic grows with every run, and the default 1 MB buffer overflows (ENOBUFS)
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'], maxBuffer: 64 * 1024 * 1024 }
  );
  return output
    .split('\n')
    .filter((line) => line.includes('\t'))
    .map((line) => {
      const [key, ...value] = line.split('\t');
      return { key, value: value.join('\t') };
    });
}

Then(
  'a NotificationSent event for that notification should reach the {string} topic, keyed by the notification id',
  { timeout: CATCH_UP_TIMEOUT_MS + 15_000 },
  async function (this: CustomWorld, topic: string) {
    const expected = delivery(this);

    const deadline = Date.now() + CATCH_UP_TIMEOUT_MS;
    let found: { key: string; value: string } | undefined;
    while (!found && Date.now() < deadline) {
      found = readTopic(topic).find((record) => record.key === expected.notificationId);
      if (!found) await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
    }

    expect(
      found,
      `no record keyed ${expected.notificationId} reached ${topic} within ${CATCH_UP_TIMEOUT_MS}ms`
    ).toBeDefined();
    const event = JSON.parse(found!.value);
    expect(event.eventType).toBe('NotificationSent');
    expect(event.aggregateId).toBe(expected.notificationId);
    expect(event.payload.notificationId).toBe(expected.notificationId);
    expect(event.payload.recipientEmail).toBe(expected.recipientEmail);
    expect(event.payload.status).toBe('SENT');
    expect(event.payload.providerMessageId).toBe(expected.providerMessageId);
  }
);

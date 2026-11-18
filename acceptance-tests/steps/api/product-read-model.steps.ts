import { After, Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { CustomWorld } from '../../support/world.js';

const POSTGRES_CONTAINER = process.env.POSTGRES_CONTAINER ?? 'acme-postgres';
const POSTGRES_USER = process.env.POSTGRES_USER ?? 'postgres';
const CATALOG_DATABASE = 'acme_products';
/** Debezium and the projector each take well under a second; the margin is for a cold start. */
const CATCH_UP_TIMEOUT_MS = 30_000;
const POLL_INTERVAL_MS = 500;
const TEMPORARY_SLUG_PREFIX = 'acceptance-catalog-';

interface Category {
  name: string;
  productCount: number;
}

/** Runs one SQL statement against the catalog database and returns its rows, one per line. */
function catalogSql(sql: string): string[] {
  const output = execFileSync(
    'docker',
    [
      'exec',
      POSTGRES_CONTAINER,
      'psql',
      '-U',
      POSTGRES_USER,
      '-d',
      CATALOG_DATABASE,
      '-v',
      'ON_ERROR_STOP=1',
      '-qAt',
      '-c',
      sql,
    ],
    { encoding: 'utf8' }
  );
  return output.split('\n').filter((line) => line.length > 0);
}

function variantId(world: CustomWorld): string {
  const id = world.getTestData<string>('catalogVariantId');
  expect(id, 'choose or add a variant first').toBeDefined();
  return id!;
}

function newProduct(world: CustomWorld): { slug: string; word: string; variantId: string } {
  const product = world.getTestData<{ slug: string; word: string; variantId: string }>(
    'catalogNewProduct'
  );
  expect(product, 'add the product first').toBeDefined();
  return product!;
}

async function availability(world: CustomWorld, id: string) {
  return world.productApiClient.get<{ availability: string }>(
    `/api/v1/inventory/availability/${id}`
  );
}

Given('the Product Service is available', async function (this: CustomWorld) {
  const response = await this.productApiClient.get<{ status: string }>('/actuator/health');
  expect(response.status).toBe(200);
  expect(response.data?.status).toBe('UP');
});

Then(
  'the category counts the Product Service reports should match the published products in the catalog database',
  { timeout: CATCH_UP_TIMEOUT_MS + 10_000 },
  async function (this: CustomWorld) {
    const expected = Object.fromEntries(
      catalogSql(
        `SELECT category || '|' || count(*) FROM products
          WHERE status = 'PUBLISHED' AND category IS NOT NULL GROUP BY category`
      ).map((row) => {
        const [name, count] = row.split('|');
        return [name, Number(count)];
      })
    );
    expect(Object.keys(expected).length, 'the catalog database has categories').toBeGreaterThan(0);

    await expect
      .poll(
        async () => {
          const response = await this.productApiClient.get<{ categories: Category[] }>(
            '/api/v1/categories'
          );
          return response.status === 200
            ? Object.fromEntries(response.data.categories.map((c) => [c.name, c.productCount]))
            : undefined;
        },
        { timeout: CATCH_UP_TIMEOUT_MS, intervals: [POLL_INTERVAL_MS] }
      )
      .toEqual(expected);
  }
);

// ---- a stock change ----

Given('a variant of a published product is in stock', async function (this: CustomWorld) {
  const [id] = catalogSql(
    `SELECT v.id FROM product_variants v JOIN products p ON p.id = v.product_id
      WHERE p.status = 'PUBLISHED' AND v.in_stock ORDER BY v.id LIMIT 1`
  );
  expect(id, 'the catalog database has an in-stock variant').toBeDefined();
  this.setTestData('catalogVariantId', id);
  expect((await availability(this, id)).data.availability).toBe('IN_STOCK');
});

When('the variant is marked out of stock in the catalog database', function (this: CustomWorld) {
  catalogSql(`UPDATE product_variants SET in_stock = false WHERE id = '${variantId(this)}'`);
});

When('the variant is marked in stock in the catalog database', function (this: CustomWorld) {
  catalogSql(`UPDATE product_variants SET in_stock = true WHERE id = '${variantId(this)}'`);
});

for (const expected of ['OUT_OF_STOCK', 'IN_STOCK']) {
  Then(
    `the Product Service should eventually report the variant ${expected}`,
    { timeout: CATCH_UP_TIMEOUT_MS + 10_000 },
    async function (this: CustomWorld) {
      const id = variantId(this);
      await expect
        .poll(async () => (await availability(this, id)).data?.availability, {
          timeout: CATCH_UP_TIMEOUT_MS,
          intervals: [POLL_INTERVAL_MS],
        })
        .toBe(expected);
    }
  );
}

// ---- a product added and deleted ----

When('a product with a variant is added to the catalog database', function (this: CustomWorld) {
  const suffix = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const slug = `${TEMPORARY_SLUG_PREFIX}${suffix}`;
  const word = `catalogtest${suffix}`;
  const [id] = catalogSql(
    `WITH p AS (
       INSERT INTO products (id, slug, name, description, price, status, category)
       VALUES (gen_random_uuid(), '${slug}', 'Acceptance ${word}', 'Added by an acceptance test', 12.34,
               'PUBLISHED', 'Acceptance')
       RETURNING id)
     INSERT INTO product_variants (id, product_id, sku, name, is_default)
     SELECT gen_random_uuid(), p.id, 'ACC-${suffix}', 'Default', true FROM p
     RETURNING id`
  );
  expect(id, 'the variant was inserted').toBeDefined();
  this.setTestData('catalogNewProduct', { slug, word, variantId: id });
});

Then(
  "the Product Service should eventually serve the product's detail",
  { timeout: CATCH_UP_TIMEOUT_MS + 10_000 },
  async function (this: CustomWorld) {
    const { slug, variantId: id } = newProduct(this);
    await expect
      .poll(
        async () => {
          const response = await this.productApiClient.get<{
            slug: string;
            variants: { id: string }[];
          }>(`/api/v1/products/${slug}`);
          return response.status === 200 ? response.data.variants.map((v) => v.id) : undefined;
        },
        { timeout: CATCH_UP_TIMEOUT_MS, intervals: [POLL_INTERVAL_MS] }
      )
      .toEqual([id]);
  }
);

async function searchSlugs(world: CustomWorld, word: string): Promise<string[]> {
  const response = await world.productApiClient.post<{ results: { slug: string }[] }>(
    '/api/v1/search',
    { query: word }
  );
  expect(response.status).toBe(200);
  return response.data.results.map((r) => r.slug);
}

Then(
  "searching for the product's unique word should find only that product",
  { timeout: CATCH_UP_TIMEOUT_MS + 10_000 },
  async function (this: CustomWorld) {
    const { slug, word } = newProduct(this);
    await expect
      .poll(() => searchSlugs(this, word), {
        timeout: CATCH_UP_TIMEOUT_MS,
        intervals: [POLL_INTERVAL_MS],
      })
      .toEqual([slug]);
  }
);

Then(
  "the Product Service should report the product's variant IN_STOCK",
  async function (this: CustomWorld) {
    const response = await availability(this, newProduct(this).variantId);
    expect(response.status).toBe(200);
    expect(response.data.availability).toBe('IN_STOCK');
  }
);

When('the product is deleted from the catalog database', function (this: CustomWorld) {
  // The variant goes with it (ON DELETE CASCADE), as three more change events
  catalogSql(`DELETE FROM products WHERE slug = '${newProduct(this).slug}'`);
});

Then(
  "the Product Service should eventually answer 404 for the product's detail",
  { timeout: CATCH_UP_TIMEOUT_MS + 10_000 },
  async function (this: CustomWorld) {
    const { slug } = newProduct(this);
    await expect
      .poll(async () => (await this.productApiClient.get(`/api/v1/products/${slug}`)).status, {
        timeout: CATCH_UP_TIMEOUT_MS,
        intervals: [POLL_INTERVAL_MS],
      })
      .toBe(404);
  }
);

Then(
  "searching for the product's unique word should find nothing",
  async function (this: CustomWorld) {
    expect(await searchSlugs(this, newProduct(this).word)).toEqual([]);
  }
);

Then(
  "the Product Service should answer 404 for the product's variant",
  async function (this: CustomWorld) {
    const response = await availability(this, newProduct(this).variantId);
    expect(response.status).toBe(404);
  }
);

// Leaves the catalog as it was, whether or not the scenario got to its own clean-up
After({ tags: '@catalog-change' }, function (this: CustomWorld) {
  catalogSql(`DELETE FROM products WHERE slug LIKE '${TEMPORARY_SLUG_PREFIX}%'`);
  const id = this.getTestData<string>('catalogVariantId');
  if (id) catalogSql(`UPDATE product_variants SET in_stock = true WHERE id = '${id}'`);
});

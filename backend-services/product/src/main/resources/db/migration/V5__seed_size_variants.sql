-- Seed size variants for Widget Starter Kit to demonstrate SizeButtonSelector
INSERT INTO product_variants (id, product_id, sku, name, size, is_default, in_stock, price_override)
SELECT
    '33333333-3333-3333-3333-000000000001',
    p.id,
    'ACME-WSK-SM',
    'Small',
    'S',
    FALSE,
    TRUE,
    49.99
FROM products p WHERE p.slug = 'widget-starter-kit'
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_variants (id, product_id, sku, name, size, is_default, in_stock, price_override)
SELECT
    '33333333-3333-3333-3333-000000000002',
    p.id,
    'ACME-WSK-MD',
    'Medium',
    'M',
    TRUE,
    TRUE,
    NULL
FROM products p WHERE p.slug = 'widget-starter-kit'
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_variants (id, product_id, sku, name, size, is_default, in_stock, price_override)
SELECT
    '33333333-3333-3333-3333-000000000003',
    p.id,
    'ACME-WSK-LG',
    'Large',
    'L',
    FALSE,
    TRUE,
    69.99
FROM products p WHERE p.slug = 'widget-starter-kit'
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_variants (id, product_id, sku, name, size, is_default, in_stock, price_override)
SELECT
    '33333333-3333-3333-3333-000000000004',
    p.id,
    'ACME-WSK-XL',
    'X-Large',
    'XL',
    FALSE,
    FALSE,
    79.99
FROM products p WHERE p.slug = 'widget-starter-kit'
ON CONFLICT (id) DO NOTHING;

-- Images for Widget Starter Kit Medium (default)
INSERT INTO product_variant_images (variant_id, url, display_order) VALUES
    ('33333333-3333-3333-3333-000000000002', 'https://placehold.co/600x400/1e3a5f/ffffff?text=Starter+Kit+Medium', 0)
ON CONFLICT DO NOTHING;

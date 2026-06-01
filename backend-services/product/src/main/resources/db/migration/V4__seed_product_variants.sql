-- Seed product variants for Gadget Pro (color variants)
INSERT INTO product_variants (id, product_id, sku, name, color, is_default, in_stock, price_override)
SELECT
    '11111111-1111-1111-1111-000000000001',
    p.id,
    'ACME-GP-BLK',
    'Black',
    'Black',
    TRUE,
    TRUE,
    NULL
FROM products p WHERE p.slug = 'gadget-pro'
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_variants (id, product_id, sku, name, color, is_default, in_stock, price_override)
SELECT
    '11111111-1111-1111-1111-000000000002',
    p.id,
    'ACME-GP-WHT',
    'White',
    'White',
    FALSE,
    TRUE,
    NULL
FROM products p WHERE p.slug = 'gadget-pro'
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_variants (id, product_id, sku, name, color, is_default, in_stock, price_override)
SELECT
    '11111111-1111-1111-1111-000000000003',
    p.id,
    'ACME-GP-SLV',
    'Silver',
    'Silver',
    FALSE,
    FALSE,
    NULL
FROM products p WHERE p.slug = 'gadget-pro'
ON CONFLICT (id) DO NOTHING;

-- Seed product variants for Noise Canceling Headphones (color + size-class variants)
INSERT INTO product_variants (id, product_id, sku, name, color, is_default, in_stock, price_override)
SELECT
    '22222222-2222-2222-2222-000000000001',
    p.id,
    'ACME-NCH-BLK-STD',
    'Midnight Black',
    'Black',
    TRUE,
    TRUE,
    NULL
FROM products p WHERE p.slug = 'noise-canceling-headphones'
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_variants (id, product_id, sku, name, color, is_default, in_stock, price_override)
SELECT
    '22222222-2222-2222-2222-000000000002',
    p.id,
    'ACME-NCH-WHT-STD',
    'Pearl White',
    'White',
    FALSE,
    TRUE,
    189.99
FROM products p WHERE p.slug = 'noise-canceling-headphones'
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_variants (id, product_id, sku, name, color, is_default, in_stock, price_override)
SELECT
    '22222222-2222-2222-2222-000000000003',
    p.id,
    'ACME-NCH-NVY-LTD',
    'Navy Limited Edition',
    'Navy',
    FALSE,
    FALSE,
    219.99
FROM products p WHERE p.slug = 'noise-canceling-headphones'
ON CONFLICT (id) DO NOTHING;

-- Variant images for Gadget Pro Black
INSERT INTO product_variant_images (variant_id, url, display_order) VALUES
    ('11111111-1111-1111-1111-000000000001', 'https://placehold.co/600x400/1a1a2e/ffffff?text=Gadget+Pro+Black+1', 0),
    ('11111111-1111-1111-1111-000000000001', 'https://placehold.co/600x400/16213e/ffffff?text=Gadget+Pro+Black+2', 1)
ON CONFLICT DO NOTHING;

-- Variant images for Gadget Pro White
INSERT INTO product_variant_images (variant_id, url, display_order) VALUES
    ('11111111-1111-1111-1111-000000000002', 'https://placehold.co/600x400/f8f8f8/333333?text=Gadget+Pro+White+1', 0),
    ('11111111-1111-1111-1111-000000000002', 'https://placehold.co/600x400/eeeeee/333333?text=Gadget+Pro+White+2', 1)
ON CONFLICT DO NOTHING;

-- Variant images for Gadget Pro Silver
INSERT INTO product_variant_images (variant_id, url, display_order) VALUES
    ('11111111-1111-1111-1111-000000000003', 'https://placehold.co/600x400/c0c0c0/333333?text=Gadget+Pro+Silver+1', 0)
ON CONFLICT DO NOTHING;

-- Variant images for Noise Canceling Headphones
INSERT INTO product_variant_images (variant_id, url, display_order) VALUES
    ('22222222-2222-2222-2222-000000000001', 'https://placehold.co/600x400/0d0d0d/ffffff?text=NCH+Black+1', 0),
    ('22222222-2222-2222-2222-000000000001', 'https://placehold.co/600x400/1a1a1a/ffffff?text=NCH+Black+2', 1)
ON CONFLICT DO NOTHING;

INSERT INTO product_variant_images (variant_id, url, display_order) VALUES
    ('22222222-2222-2222-2222-000000000002', 'https://placehold.co/600x400/f5f5f5/333333?text=NCH+White+1', 0)
ON CONFLICT DO NOTHING;

INSERT INTO product_variant_images (variant_id, url, display_order) VALUES
    ('22222222-2222-2222-2222-000000000003', 'https://placehold.co/600x400/1b2a4a/ffffff?text=NCH+Navy+1', 0)
ON CONFLICT DO NOTHING;

-- Tier pricing for Gadget Pro Black (demonstrates AC-08)
INSERT INTO product_variant_tier_pricing (variant_id, min_quantity, price) VALUES
    ('11111111-1111-1111-1111-000000000001', 3, 109.99),
    ('11111111-1111-1111-1111-000000000001', 10, 99.99)
ON CONFLICT DO NOTHING;

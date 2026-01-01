# Epic 001: Product Catalog

## Overview

The Product Catalog is the foundational component of the ACME, Inc. e-commerce platform. It provides the data model, API, and user interfaces for managing and displaying products to customers.

## Goals

- Enable customers to browse, search, and filter products
- Support rich product information including images, descriptions, pricing, and variants
- Provide administrative capabilities for product management
- Ensure scalability for a growing product inventory
- Enable integration with inventory, pricing, and order systems

## Domain Model

### Core Entities

#### Product

The primary entity representing an item for sale.

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique identifier |
| sku | string | Stock keeping unit (unique) |
| name | string | Product display name |
| slug | string | URL-friendly identifier |
| description | text | Full product description |
| shortDescription | string | Brief summary for listings |
| status | enum | draft, active, archived |
| createdAt | timestamp | Creation date |
| updatedAt | timestamp | Last modification date |

#### ProductVariant

Represents variations of a product (size, color, etc.).

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique identifier |
| productId | UUID | Parent product reference |
| sku | string | Variant-specific SKU |
| name | string | Variant display name |
| price | decimal | Base price |
| compareAtPrice | decimal | Original price (for sales) |
| weight | decimal | Shipping weight |
| inventoryQuantity | integer | Available stock |
| attributes | jsonb | Key-value variant attributes |

#### Category

Hierarchical organization of products.

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique identifier |
| parentId | UUID | Parent category (nullable) |
| name | string | Category name |
| slug | string | URL-friendly identifier |
| description | text | Category description |
| displayOrder | integer | Sort order |

#### ProductImage

Media assets associated with products.

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique identifier |
| productId | UUID | Parent product reference |
| variantId | UUID | Optional variant reference |
| url | string | Image URL |
| altText | string | Accessibility text |
| displayOrder | integer | Sort order |
| isPrimary | boolean | Primary image flag |

#### ProductAttribute

Flexible attributes for filtering and display.

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique identifier |
| name | string | Attribute name (e.g., "Material") |
| type | enum | text, number, boolean, select |
| values | array | Allowed values (for select type) |

### Entity Relationships

```
Category (1) ──────── (n) Product
Product (1) ──────── (n) ProductVariant
Product (1) ──────── (n) ProductImage
ProductVariant (1) ── (n) ProductImage
Product (n) ──────── (n) ProductAttribute (with values)
```

## API Design

### RESTful Endpoints

#### Public API (Customer-facing)

```
GET    /api/v1/products                 # List products (paginated)
GET    /api/v1/products/:slug           # Get product by slug
GET    /api/v1/products/search          # Full-text search
GET    /api/v1/categories               # List categories (tree)
GET    /api/v1/categories/:slug         # Get category with products
```

#### Admin API

```
POST   /api/v1/admin/products           # Create product
PUT    /api/v1/admin/products/:id       # Update product
DELETE /api/v1/admin/products/:id       # Archive product
POST   /api/v1/admin/products/:id/variants    # Add variant
PUT    /api/v1/admin/products/:id/variants/:vid  # Update variant
POST   /api/v1/admin/products/:id/images      # Upload image
DELETE /api/v1/admin/products/:id/images/:iid # Remove image
POST   /api/v1/admin/categories         # Create category
PUT    /api/v1/admin/categories/:id     # Update category
DELETE /api/v1/admin/categories/:id     # Delete category
```

### Query Parameters

| Parameter | Description | Example |
|-----------|-------------|---------|
| page | Page number | `?page=2` |
| limit | Items per page (max 100) | `?limit=20` |
| sort | Sort field and direction | `?sort=price:asc` |
| category | Filter by category slug | `?category=electronics` |
| minPrice | Minimum price filter | `?minPrice=10` |
| maxPrice | Maximum price filter | `?maxPrice=100` |
| attributes | Attribute filters | `?attributes[color]=red` |
| q | Search query | `?q=wireless headphones` |

## User Interface

### Customer-Facing Pages

1. **Product Listing Page (PLP)**
   - Grid/list view toggle
   - Faceted filtering (category, price, attributes)
   - Sorting options
   - Pagination
   - Quick view modal

2. **Product Detail Page (PDP)**
   - Image gallery with zoom
   - Variant selector
   - Price display with sale indicators
   - Add to cart functionality
   - Related products
   - Breadcrumb navigation

3. **Search Results**
   - Autocomplete suggestions
   - Search result highlighting
   - Filter refinement
   - "Did you mean" suggestions

### Admin Pages

1. **Product List**
   - Bulk actions (archive, publish)
   - Quick edit inline
   - Status filters
   - Export functionality

2. **Product Editor**
   - Rich text description editor
   - Drag-and-drop image upload
   - Variant matrix builder
   - SEO metadata fields
   - Category assignment

3. **Category Manager**
   - Drag-and-drop tree organization
   - Bulk product assignment

## Technical Architecture

### Technology Stack

| Layer | Technology | Rationale |
|-------|------------|-----------|
| Frontend | React + TypeScript | Component-based, type-safe |
| State | TanStack Query | Server state management |
| Styling | Tailwind CSS | Utility-first, consistent |
| API | Node.js + Express | JavaScript ecosystem |
| Database | PostgreSQL | Relational, JSONB support |
| Search | PostgreSQL full-text | Start simple, migrate if needed |
| Images | S3 + CloudFront | Scalable media delivery |
| Cache | Redis | Session and query caching |

### Project Structure

```
src/
├── api/
│   ├── routes/
│   │   ├── products.ts
│   │   ├── categories.ts
│   │   └── admin/
│   ├── controllers/
│   ├── middleware/
│   └── validators/
├── domain/
│   ├── entities/
│   │   ├── Product.ts
│   │   ├── ProductVariant.ts
│   │   ├── Category.ts
│   │   └── ProductImage.ts
│   ├── repositories/
│   └── services/
├── infrastructure/
│   ├── database/
│   │   ├── migrations/
│   │   └── seeds/
│   ├── storage/
│   └── cache/
└── web/
    ├── components/
    │   ├── product/
    │   └── category/
    ├── pages/
    └── hooks/
```

### Performance Considerations

- **Database indexing**: SKU, slug, category_id, status, full-text search
- **Image optimization**: Multiple sizes, WebP format, lazy loading
- **Caching strategy**: Category tree (long TTL), product lists (short TTL)
- **Pagination**: Cursor-based for large datasets

## Security

- Admin endpoints require authentication and authorization
- Rate limiting on public search endpoints
- Input validation and sanitization
- SQL injection prevention via parameterized queries
- XSS prevention in product descriptions

## Integration Points

| System | Integration | Purpose |
|--------|-------------|---------|
| Inventory | Event-based | Stock level updates |
| Pricing | API call | Dynamic pricing rules |
| Orders | Read-only | Product snapshot at order time |
| Analytics | Event stream | View and search tracking |

## Success Metrics

- Page load time < 2 seconds (P95)
- Search results < 500ms
- Admin product save < 1 second
- Zero downtime deployments
- 99.9% API availability

## Implementation Phases

### Phase 1: Foundation

- Database schema and migrations
- Core Product and Category entities
- Basic CRUD API endpoints
- Seed data for development

### Phase 2: Customer Experience

- Product listing page with filtering
- Product detail page
- Basic search functionality
- Image upload and display

### Phase 3: Admin Interface

- Product management UI
- Category tree management
- Bulk operations
- Image management

### Phase 4: Enhancement

- Advanced search with facets
- Product variants
- Performance optimization
- Analytics integration

## Open Questions

1. Do we need multi-currency pricing support?
2. What is the expected product catalog size (affects search strategy)?
3. Are there existing brand guidelines for the UI?
4. What authentication system will be used for admin access?
5. Do we need localization for product content?

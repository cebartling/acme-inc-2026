// =============================================================================
// MongoDB Initialization Script
// Sets up replica set and creates application databases/collections
// =============================================================================

// Note: Replica set initialization is handled by the healthcheck in docker-compose
// This script runs after MongoDB is ready and sets up application-specific resources
// Authentication is disabled for local development

// =============================================================================
// Orders Query Store
// =============================================================================
db = db.getSiblingDB('acme_orders_query');

db.createCollection('orders');
db.orders.createIndex({ 'orderId': 1 }, { unique: true });
db.orders.createIndex({ 'customerId': 1 });
db.orders.createIndex({ 'status': 1 });
db.orders.createIndex({ 'createdAt': -1 });

db.createCollection('order_items');
db.order_items.createIndex({ 'orderId': 1 });
db.order_items.createIndex({ 'productId': 1 });

// =============================================================================
// Inventory Query Store
// =============================================================================
db = db.getSiblingDB('acme_inventory_query');

db.createCollection('products');
db.products.createIndex({ 'productId': 1 }, { unique: true });
db.products.createIndex({ 'sku': 1 }, { unique: true });
db.products.createIndex({ 'category': 1 });
db.products.createIndex({ 'name': 'text', 'description': 'text' });

db.createCollection('stock_levels');
db.stock_levels.createIndex({ 'productId': 1 }, { unique: true });
db.stock_levels.createIndex({ 'warehouseId': 1 });

// =============================================================================
// Customers Query Store
// =============================================================================
db = db.getSiblingDB('acme_customers_query');

db.createCollection('customers');
db.customers.createIndex({ 'customerId': 1 }, { unique: true });
db.customers.createIndex({ 'email': 1 }, { unique: true });
db.customers.createIndex({ 'lastName': 1, 'firstName': 1 });

db.createCollection('addresses');
db.addresses.createIndex({ 'customerId': 1 });
db.addresses.createIndex({ 'type': 1 });

// =============================================================================
// Payments Query Store
// =============================================================================
db = db.getSiblingDB('acme_payments_query');

db.createCollection('payments');
db.payments.createIndex({ 'paymentId': 1 }, { unique: true });
db.payments.createIndex({ 'orderId': 1 });
db.payments.createIndex({ 'customerId': 1 });
db.payments.createIndex({ 'status': 1 });
db.payments.createIndex({ 'createdAt': -1 });

db.createCollection('refunds');
db.refunds.createIndex({ 'refundId': 1 }, { unique: true });
db.refunds.createIndex({ 'paymentId': 1 });
db.refunds.createIndex({ 'status': 1 });

print('MongoDB initialization completed successfully');

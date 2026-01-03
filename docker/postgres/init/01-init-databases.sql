-- =============================================================================
-- PostgreSQL Initialization Script
-- Creates databases and users for ACME Inc. microservices
-- =============================================================================

-- Create application databases for each service
CREATE DATABASE acme_orders;
CREATE DATABASE acme_inventory;
CREATE DATABASE acme_customers;
CREATE DATABASE acme_payments;

-- Create application user with appropriate permissions
CREATE USER acme_app WITH PASSWORD 'acme_app_password';

-- Grant privileges on all databases
GRANT ALL PRIVILEGES ON DATABASE acme_orders TO acme_app;
GRANT ALL PRIVILEGES ON DATABASE acme_inventory TO acme_app;
GRANT ALL PRIVILEGES ON DATABASE acme_customers TO acme_app;
GRANT ALL PRIVILEGES ON DATABASE acme_payments TO acme_app;

-- Configure logical replication for Debezium CDC
-- Note: The Debezium PostgreSQL image already has wal_level=logical configured

-- Connect to each database and set up replication permissions
\c acme_orders
GRANT ALL ON SCHEMA public TO acme_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO acme_app;
CREATE PUBLICATION dbz_publication FOR ALL TABLES;

\c acme_inventory
GRANT ALL ON SCHEMA public TO acme_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO acme_app;
CREATE PUBLICATION dbz_publication FOR ALL TABLES;

\c acme_customers
GRANT ALL ON SCHEMA public TO acme_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO acme_app;
CREATE PUBLICATION dbz_publication FOR ALL TABLES;

\c acme_payments
GRANT ALL ON SCHEMA public TO acme_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO acme_app;
CREATE PUBLICATION dbz_publication FOR ALL TABLES;

-- Return to default database
\c acme

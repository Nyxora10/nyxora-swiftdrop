-- docker/postgres/init-multi-db.sql
-- Initializes the three isolated logical databases for SwiftDrop microservices

CREATE DATABASE auth_db;
CREATE DATABASE parcel_db;
CREATE DATABASE notification_db;

-- ========================================================
-- DATABASE 1: auth_db (Identity, RBAC, OAuth & Sessions)
-- ========================================================
\c auth_db;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Roles Table
CREATE TABLE IF NOT EXISTS roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,      -- 'ROLE_CUSTOMER', 'ROLE_MERCHANT', 'ROLE_COURIER', 'ROLE_ADMIN'
    description VARCHAR(255)
);

-- 2. Users Table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_id INT NOT NULL REFERENCES roles(id),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),            -- Nullable for Google OAuth 2.0 social login
    full_name VARCHAR(150) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'PENDING_APPROVAL', 'SUSPENDED', 'REJECTED'
    status_reason VARCHAR(500),
    auth_provider VARCHAR(30) NOT NULL DEFAULT 'LOCAL', -- 'LOCAL', 'GOOGLE'
    provider_id VARCHAR(100) UNIQUE,       -- Google 'sub' claim identifier
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Merchants Table (1:1 with users)
CREATE TABLE IF NOT EXISTS merchants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    business_name VARCHAR(200) NOT NULL,
    registration_number VARCHAR(100) UNIQUE NOT NULL,
    webhook_url VARCHAR(500),
    webhook_secret VARCHAR(255),           -- HMAC-SHA256 signing secret for outbound webhook events
    oauth_client_id VARCHAR(100) UNIQUE,   -- Machine-to-machine OAuth 2.0 Client ID
    oauth_client_secret_hash VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Couriers Table (1:1 with users)
CREATE TABLE IF NOT EXISTS couriers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_zone_id INT,                  -- Logical reference to parcel_db.delivery_zones.id
    vehicle_type VARCHAR(50) NOT NULL,     -- 'MOTORCYCLE', 'THREE_WHEELER', 'VAN', 'TRUCK'
    license_plate VARCHAR(50) UNIQUE NOT NULL,
    is_available BOOLEAN DEFAULT TRUE,
    active_delivery_count INT DEFAULT 0,   -- Atomic workload monitor for Dispatcher (UC19)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. Refresh Tokens Table (JWT Session Management)
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) UNIQUE NOT NULL, -- SHA-256 cryptographic hash (theft-proof)
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);


-- ========================================================
-- DATABASE 2: parcel_db (Parcel, Pricing & Dispatch Service)
-- ========================================================
\c parcel_db;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Delivery Zones (Geographical Districts)
CREATE TABLE IF NOT EXISTS delivery_zones (
    id SERIAL PRIMARY KEY,
    zone_code VARCHAR(20) UNIQUE NOT NULL, -- e.g. 'WP-COL-01'
    zone_name VARCHAR(100) NOT NULL,
    district VARCHAR(50) NOT NULL,
    base_surcharge DECIMAL(10,2) DEFAULT 0.00,
    is_active BOOLEAN DEFAULT TRUE
);

-- 2. Pricing Rules (Dynamic Strategy Pattern Matrix)
CREATE TABLE IF NOT EXISTS pricing_rules (
    id SERIAL PRIMARY KEY,
    origin_zone_id INT NOT NULL REFERENCES delivery_zones(id),
    destination_zone_id INT NOT NULL REFERENCES delivery_zones(id),
    base_fee DECIMAL(10,2) NOT NULL,
    rate_per_kg DECIMAL(10,2) NOT NULL,
    parcel_size VARCHAR(30) NOT NULL,      -- 'SMALL', 'MEDIUM', 'LARGE'
    size_multiplier DECIMAL(4,2) DEFAULT 1.00,
    CONSTRAINT uk_pricing_rule UNIQUE (origin_zone_id, destination_zone_id, parcel_size)
);

-- 3. Parcels Table (Core Delivery Bookings)
CREATE TABLE IF NOT EXISTS parcels (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tracking_number VARCHAR(50) UNIQUE NOT NULL, -- Format: SD-YYYYMMDD-XXXXXX
    sender_user_id UUID NOT NULL,          -- Logical reference to auth_db.users.id
    merchant_id UUID,                      -- Logical reference to auth_db.merchants.id
    assigned_courier_id UUID,              -- Logical reference to auth_db.couriers.id
    origin_zone_id INT NOT NULL REFERENCES delivery_zones(id),
    destination_zone_id INT NOT NULL REFERENCES delivery_zones(id),
    pickup_address TEXT NOT NULL,
    dropoff_address TEXT NOT NULL,
    recipient_name VARCHAR(150) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    weight_kg DECIMAL(6,2) NOT NULL,
    parcel_size VARCHAR(30) NOT NULL,
    estimated_fee DECIMAL(10,2) NOT NULL,
    current_status VARCHAR(40) NOT NULL DEFAULT 'CREATED', -- 'CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Parcel Status History (Timeline & Proof of Delivery)
CREATE TABLE IF NOT EXISTS parcel_status_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    parcel_id UUID NOT NULL REFERENCES parcels(id) ON DELETE CASCADE,
    status VARCHAR(40) NOT NULL,
    updated_by_user_id UUID NOT NULL,      -- Courier or Dispatcher UUID
    delivery_note TEXT,
    recipient_signature_name VARCHAR(150),
    proof_photo_url VARCHAR(500),          -- S3/MinIO photo URL
    failure_reason VARCHAR(100),           -- Required when status = 'FAILED'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_parcels_tracking ON parcels(tracking_number);
CREATE INDEX IF NOT EXISTS idx_parcels_sender ON parcels(sender_user_id);
CREATE INDEX IF NOT EXISTS idx_parcels_merchant ON parcels(merchant_id);
CREATE INDEX IF NOT EXISTS idx_parcels_courier_status ON parcels(assigned_courier_id, current_status);
CREATE INDEX IF NOT EXISTS idx_status_history_parcel ON parcel_status_history(parcel_id);


-- ========================================================
-- DATABASE 3: notification_db (Notification & Webhook Worker)
-- ========================================================
\c notification_db;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Webhook Delivery Logs (Outbound Retry Engine)
CREATE TABLE IF NOT EXISTS webhook_delivery_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    parcel_id UUID NOT NULL,               -- Logical reference to parcel_db.parcels.id
    merchant_id UUID NOT NULL,             -- Logical reference to auth_db.merchants.id
    event_type VARCHAR(50) NOT NULL,       -- 'parcel.status.updated'
    payload JSONB NOT NULL,
    http_status_code INT,
    retry_count INT DEFAULT 0,             -- Max 5 attempts
    delivery_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',  -- 'PENDING', 'DELIVERED', 'RETRYING', 'DEAD_LETTER'
    next_retry_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_webhook_logs_retry ON webhook_delivery_logs(delivery_status, next_retry_at);


-- 1. Webhook Delivery Logs (Outbound Retry Engine)
CREATE TABLE IF NOT EXISTS webhook_delivery_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    parcel_id UUID NOT NULL,               -- Logical reference to parcel_db.parcels.id
    merchant_id UUID NOT NULL,             -- Logical reference to auth_db.merchants.id
    event_type VARCHAR(50) NOT NULL,       -- 'parcel.status.updated'
    payload JSONB NOT NULL,
    http_status_code INT,
    retry_count INT DEFAULT 0,             -- Max 5 attempts
    delivery_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',  -- 'PENDING', 'DELIVERED', 'RETRYING', 'DEAD_LETTER'
    next_retry_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_webhook_logs_retry ON webhook_delivery_logs(delivery_status, next_retry_at);
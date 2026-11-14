-- docker/postgres/seed-data.sql  (Module 10 – deterministic demo seed data)
-- Runs once, right after init-multi-db.sql, when the PostgreSQL volume is created
-- (docker-entrypoint-initdb.d runs files in alphabetical order).
-- Safe to run again: every insert uses ON CONFLICT DO NOTHING.
--
-- Demo accounts (public DEMO credentials, same as the services' DataInitializer):
--   admin@swiftdrop.lk            Admin@SwiftDrop2026!   ROLE_ADMIN
--   customer.kasun@swiftdrop.lk   Customer@123           ROLE_CUSTOMER
--   courier.kamal@swiftdrop.lk    Courier@123            ROLE_COURIER  (Colombo, motorcycle)
--   courier.nimal@swiftdrop.lk    Courier@123            ROLE_COURIER  (Gampaha, three-wheeler)
--   merchant.daraz@swiftdrop.lk   Merchant@123           ROLE_MERCHANT
--     OAuth2 client_credentials:  client_id = mch_daraz_client_01   client_secret = sec_daraz_secret_2026
--     Webhook HMAC secret:        whsec_daraz_hmac_secret_2026
-- Passwords and the client secret are stored as BCrypt hashes (pgcrypto), which Spring's
-- BCryptPasswordEncoder verifies. Fixed UUIDs keep IDs identical on every machine.

-- ========================================================
-- auth_db: roles, users, couriers, merchant
-- ========================================================
\c auth_db;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO roles (name, description) VALUES
    ('ROLE_CUSTOMER', 'Standard retail delivery customer'),
    ('ROLE_MERCHANT', 'E-commerce partner with API and webhook integration'),
    ('ROLE_COURIER',  'Delivery courier and driver executing dispatch runs'),
    ('ROLE_ADMIN',    'Platform administrator and operations dispatcher')
ON CONFLICT (name) DO NOTHING;

INSERT INTO users (id, role_id, email, password_hash, full_name, phone_number, status, auth_provider)
SELECT v.id::uuid, r.id, v.email, crypt(v.pw, gen_salt('bf', 10)), v.full_name, v.phone, 'ACTIVE', 'LOCAL'
FROM (VALUES
    ('a0000000-0000-4000-8000-000000000001', 'ROLE_ADMIN',    'admin@swiftdrop.lk',          'Admin@SwiftDrop2026!', 'SwiftDrop Administrator', '+94770000000'),
    ('c0000000-0000-4000-8000-000000000001', 'ROLE_CUSTOMER', 'customer.kasun@swiftdrop.lk', 'Customer@123',         'Kasun Perera',            '+94771234567'),
    ('d0000000-0000-4000-8000-000000000001', 'ROLE_COURIER',  'courier.kamal@swiftdrop.lk',  'Courier@123',          'Kamal Perera',            '+94777654321'),
    ('d0000000-0000-4000-8000-000000000002', 'ROLE_COURIER',  'courier.nimal@swiftdrop.lk',  'Courier@123',          'Nimal Silva',             '+94779876543'),
    ('e0000000-0000-4000-8000-000000000001', 'ROLE_MERCHANT', 'merchant.daraz@swiftdrop.lk', 'Merchant@123',         'Daraz Logistics Hub',     '+94112345678')
) AS v(id, role_name, email, pw, full_name, phone)
JOIN roles r ON r.name = v.role_name
ON CONFLICT (email) DO NOTHING;

-- Couriers (assigned_zone_id refers to parcel_db.delivery_zones.id below: 1 = Colombo, 2 = Gampaha)
INSERT INTO couriers (id, user_id, assigned_zone_id, vehicle_type, license_plate, is_available, active_delivery_count)
SELECT v.id::uuid, u.id, v.zone_id, v.vehicle, v.plate, TRUE, 0
FROM (VALUES
    ('d1000000-0000-4000-8000-000000000001', 'courier.kamal@swiftdrop.lk', 1, 'MOTORCYCLE',    'WP-BAM-1234'),
    ('d1000000-0000-4000-8000-000000000002', 'courier.nimal@swiftdrop.lk', 2, 'THREE_WHEELER', 'WP-CAB-5678')
) AS v(id, email, zone_id, vehicle, plate)
JOIN users u ON u.email = v.email
ON CONFLICT (user_id) DO NOTHING;

-- Merchant with working OAuth2 client_credentials and webhook settings
INSERT INTO merchants (id, user_id, business_name, registration_number, webhook_url, webhook_secret,
                       oauth_client_id, oauth_client_secret_hash)
SELECT 'e1000000-0000-4000-8000-000000000001'::uuid, u.id, 'Daraz Sri Lanka PVT LTD', 'PV-123456',
       'http://localhost:8083/api/v1/webhooks/demo-receiver', 'whsec_daraz_hmac_secret_2026',
       'mch_daraz_client_01', crypt('sec_daraz_secret_2026', gen_salt('bf', 10))
FROM users u
WHERE u.email = 'merchant.daraz@swiftdrop.lk'
ON CONFLICT (user_id) DO NOTHING;

-- ========================================================
-- parcel_db: Sri Lankan delivery zones and pricing rules
-- ========================================================
\c parcel_db;

INSERT INTO delivery_zones (id, zone_code, zone_name, district, base_surcharge, is_active) VALUES
    (1, 'WP-COL-01', 'Colombo Central Metro',     'Colombo',    50.00, TRUE),
    (2, 'WP-GAM-01', 'Gampaha Industrial Hub',    'Gampaha',    75.00, TRUE),
    (3, 'CP-KAN-01', 'Kandy Central Highlands',   'Kandy',     100.00, TRUE),
    (4, 'SP-GAL-01', 'Galle Southern Corridor',   'Galle',     120.00, TRUE),
    (5, 'NP-JAF-01', 'Jaffna Northern Peninsula', 'Jaffna',    200.00, TRUE),
    (6, 'NW-KUR-01', 'Kurunegala Wayamba Zone',   'Kurunegala', 90.00, TRUE)
ON CONFLICT (zone_code) DO NOTHING;

-- Keep the id sequence after the explicit ids, so zones created later in the admin UI get 7, 8, ...
SELECT setval(pg_get_serial_sequence('delivery_zones', 'id'), (SELECT MAX(id) FROM delivery_zones));

-- Pricing matrix: every origin x destination x size (6 x 6 x 3 = 108 rules), same values as
-- ParcelDataInitializer: base fee LKR 350 inside a zone / 450 between zones, LKR 80 per kg,
-- size multiplier SMALL 1.00, MEDIUM 1.20, LARGE 1.50.
INSERT INTO pricing_rules (origin_zone_id, destination_zone_id, base_fee, rate_per_kg, parcel_size, size_multiplier)
SELECT o.id, d.id,
       CASE WHEN o.id = d.id THEN 350.00 ELSE 450.00 END,
       80.00, s.size, s.multiplier
FROM delivery_zones o
CROSS JOIN delivery_zones d
CROSS JOIN (VALUES ('SMALL', 1.00), ('MEDIUM', 1.20), ('LARGE', 1.50)) AS s(size, multiplier)
WHERE o.zone_code IN ('WP-COL-01','WP-GAM-01','CP-KAN-01','SP-GAL-01','NP-JAF-01','NW-KUR-01')
  AND d.zone_code IN ('WP-COL-01','WP-GAM-01','CP-KAN-01','SP-GAL-01','NP-JAF-01','NW-KUR-01')
ORDER BY o.id, d.id, s.multiplier
ON CONFLICT ON CONSTRAINT uk_pricing_rule DO NOTHING;

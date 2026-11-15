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

INSERT INTO delivery_zones (id, zone_code, zone_name, district, hub_location, base_surcharge, is_active) VALUES
    (1,  'WP-COL-01', 'Colombo Central Hub',       'Colombo',       'No. 120, Baseline Road, Colombo 09',                    50.00, TRUE),
    (2,  'WP-GAM-01', 'Gampaha District Hub',      'Gampaha',       'No. 45, Colombo Road, Gampaha',                         75.00, TRUE),
    (3,  'WP-KAL-01', 'Kalutara District Hub',     'Kalutara',      'No. 78, Galle Road, Kalutara South',                    70.00, TRUE),
    (4,  'CP-KAN-01', 'Kandy District Hub',        'Kandy',         'No. 32, William Gopallawa Mawatha, Kandy',             100.00, TRUE),
    (5,  'CP-MTL-01', 'Matale District Hub',       'Matale',        'No. 18, Kandy-Jaffna Highway, Matale',                 110.00, TRUE),
    (6,  'CP-NUW-01', 'Nuwara Eliya District Hub', 'Nuwara Eliya', 'No. 25, Badulla Road, Nuwara Eliya',                   140.00, TRUE),
    (7,  'SP-GAL-01', 'Galle District Hub',        'Galle',         'No. 90, Wakwella Road, Galle',                         120.00, TRUE),
    (8,  'SP-MTR-01', 'Matara District Hub',       'Matara',        'No. 55, Akuressa Road, Matara',                        130.00, TRUE),
    (9,  'SP-HAM-01', 'Hambantota District Hub',   'Hambantota',    'No. 14, Main Street, Hambantota',                      150.00, TRUE),
    (10, 'NP-JAF-01', 'Jaffna District Hub',       'Jaffna',        'No. 64, KKS Road, Jaffna',                             200.00, TRUE),
    (11, 'NP-KIL-01', 'Kilinochchi District Hub',  'Kilinochchi',   'No. 22, A9 Road, Kilinochchi',                         210.00, TRUE),
    (12, 'NP-MAN-01', 'Mannar District Hub',       'Mannar',        'No. 11, Hospital Road, Mannar',                        220.00, TRUE),
    (13, 'NP-MUL-01', 'Mullaitivu District Hub',   'Mullaitivu',    'No. 16, Beach Road, Mullaitivu',                       230.00, TRUE),
    (14, 'NP-VAV-01', 'Vavuniya District Hub',     'Vavuniya',      'No. 80, Kandy Road, Vavuniya',                         180.00, TRUE),
    (15, 'EP-BAT-01', 'Batticaloa District Hub',   'Batticaloa',    'No. 36, Trinco Road, Batticaloa',                      190.00, TRUE),
    (16, 'EP-AMP-01', 'Ampara District Hub',       'Ampara',        'No. 27, D.S. Senanayake Road, Ampara',                 200.00, TRUE),
    (17, 'EP-TRI-01', 'Trincomalee District Hub',  'Trincomalee',   'No. 48, Kandy Road, Trincomalee',                      195.00, TRUE),
    (18, 'NW-KUR-01', 'Kurunegala District Hub',   'Kurunegala',    'No. 70, Dambulla Road, Kurunegala',                     90.00, TRUE),
    (19, 'NW-PUT-01', 'Puttalam District Hub',     'Puttalam',      'No. 29, Colombo Road, Puttalam',                       120.00, TRUE),
    (20, 'NC-ANU-01', 'Anuradhapura District Hub', 'Anuradhapura',  'No. 88, Maithripala Senanayake Mawatha, Anuradhapura', 160.00, TRUE),
    (21, 'NC-POL-01', 'Polonnaruwa District Hub',  'Polonnaruwa',   'No. 21, Habarana Road, Kaduruwela',                    170.00, TRUE),
    (22, 'UV-BAD-01', 'Badulla District Hub',      'Badulla',       'No. 40, Passara Road, Badulla',                        165.00, TRUE),
    (23, 'UV-MON-01', 'Monaragala District Hub',   'Monaragala',    'No. 19, Wellawaya Road, Monaragala',                   180.00, TRUE),
    (24, 'SB-RAT-01', 'Ratnapura District Hub',    'Ratnapura',     'No. 66, Colombo Road, Ratnapura',                      110.00, TRUE),
    (25, 'SB-KEG-01', 'Kegalle District Hub',      'Kegalle',       'No. 34, Colombo-Kandy Road, Kegalle',                   95.00, TRUE)
ON CONFLICT (zone_code) DO NOTHING;

-- Keep the id sequence after the explicit ids, so zones created later in the admin UI get 26, 27, ...
SELECT setval(pg_get_serial_sequence('delivery_zones', 'id'), (SELECT MAX(id) FROM delivery_zones));

-- Six fallback rules cover all 25 x 25 district pairs. pricing_rules remains available
-- for exceptional origin/destination overrides only.
INSERT INTO default_pricing_rules (route_type, parcel_size, base_fee, rate_per_kg, size_multiplier) VALUES
    ('SAME_ZONE',      'SMALL',  350.00, 80.00, 1.00),
    ('SAME_ZONE',      'MEDIUM', 350.00, 80.00, 1.20),
    ('SAME_ZONE',      'LARGE',  350.00, 80.00, 1.50),
    ('DIFFERENT_ZONE', 'SMALL',  450.00, 80.00, 1.00),
    ('DIFFERENT_ZONE', 'MEDIUM', 450.00, 80.00, 1.20),
    ('DIFFERENT_ZONE', 'LARGE',  450.00, 80.00, 1.50)
ON CONFLICT ON CONSTRAINT uk_default_pricing_rule DO NOTHING;

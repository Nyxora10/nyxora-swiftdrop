-- Seed fresh test parcels for Courier Kamal Perera (ca337776-afec-460c-886b-700db90f7ec8)
-- Used for testing Courier Runsheet, S3 Direct Upload (Presigned URL), and Delivery History

\c parcel_db;

-- 1. Clean existing test parcels
DELETE FROM parcel_status_history WHERE parcel_id IN (SELECT id FROM parcels WHERE tracking_number LIKE 'SD-%');
DELETE FROM parcels WHERE tracking_number LIKE 'SD-%';

-- 2. Insert 2 Fresh ASSIGNED Parcels
INSERT INTO parcels (
    id, tracking_number, sender_user_id, merchant_id, assigned_courier_id,
    origin_zone_id, destination_zone_id, pickup_address, dropoff_address,
    recipient_name, recipient_phone, weight_kg, parcel_size, estimated_fee,
    current_status, created_at, updated_at
) VALUES
(
    'a1111111-1111-4111-8111-111111111101',
    'SD-20261121-005001',
    '8cc7c504-a54f-435e-b0f3-8c69af2713b1',
    '1d4376a6-e6ad-402d-8997-cb3432a35c89',
    'ca337776-afec-460c-886b-700db90f7ec8',
    1, 3,
    'No. 45, Galle Road, Bambalapitiya, Colombo 04',
    'No. 88, Peradeniya Road, Kandy',
    'Anura Kumara',
    '+94771234567',
    1.50,
    'SMALL',
    650.00,
    'ASSIGNED',
    NOW() - INTERVAL '15 minutes',
    NOW() - INTERVAL '10 minutes'
),
(
    'a1111111-1111-4111-8111-111111111102',
    'SD-20261121-005002',
    '8cc7c504-a54f-435e-b0f3-8c69af2713b1',
    '1d4376a6-e6ad-402d-8997-cb3432a35c89',
    'ca337776-afec-460c-886b-700db90f7ec8',
    1, 2,
    'Daraz Fulfillment Center, Peliyagoda',
    'No. 124, Kandy Road, Kadawatha, Gampaha',
    'Samanthi Perera',
    '+94719876543',
    2.80,
    'MEDIUM',
    520.00,
    'ASSIGNED',
    NOW() - INTERVAL '10 minutes',
    NOW() - INTERVAL '5 minutes'
);

INSERT INTO parcel_status_history (id, parcel_id, status, updated_by_user_id, delivery_note, created_at)
VALUES
(gen_random_uuid(), 'a1111111-1111-4111-8111-111111111101', 'CREATED', '8cc7c504-a54f-435e-b0f3-8c69af2713b1', 'Order created', NOW() - INTERVAL '15 minutes'),
(gen_random_uuid(), 'a1111111-1111-4111-8111-111111111101', 'ASSIGNED', 'b66123d7-96cc-4461-99aa-b40f786757ea', 'Assigned to Courier Kamal Perera', NOW() - INTERVAL '10 minutes'),
(gen_random_uuid(), 'a1111111-1111-4111-8111-111111111102', 'CREATED', '8cc7c504-a54f-435e-b0f3-8c69af2713b1', 'Order created', NOW() - INTERVAL '10 minutes'),
(gen_random_uuid(), 'a1111111-1111-4111-8111-111111111102', 'ASSIGNED', 'b66123d7-96cc-4461-99aa-b40f786757ea', 'Assigned to Courier Kamal Perera', NOW() - INTERVAL '5 minutes');

\c auth_db;
UPDATE couriers SET active_delivery_count = 2 WHERE id = 'ca337776-afec-460c-886b-700db90f7ec8';

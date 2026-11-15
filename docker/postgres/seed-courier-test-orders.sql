-- Seed fresh test parcels for Courier Kamal Perera (dd4480b8-1275-4c4c-b8ff-06f2bfff0558)
-- Used for testing Courier Runsheet, Photo/Signature upload, and Delivery History

\c parcel_db;

-- 1. Parcel 1: OUT_FOR_DELIVERY (Ready for Doorstep Photo + Signature Delivery)
INSERT INTO parcels (
    id, tracking_number, sender_user_id, merchant_id, assigned_courier_id,
    origin_zone_id, destination_zone_id, pickup_address, dropoff_address,
    recipient_name, recipient_phone, weight_kg, parcel_size, estimated_fee,
    current_status, created_at, updated_at
) VALUES (
    'c1111111-1111-4111-8111-111111111111',
    'SD-20261114-001010',
    '8cc7c504-a54f-435e-b0f3-8c69af2713b1',
    '1d4376a6-e6ad-402d-8997-cb3432a35c89',
    'dd4480b8-1275-4c4c-b8ff-06f2bfff0558',
    1, 1,
    'Daraz Fulfillment Center, Peliyagoda',
    'No. 42, Galle Road, Kollupitiya, Colombo 03',
    'Nimali Wijesinghe',
    '+94712345678',
    1.50,
    'SMALL',
    470.00,
    'OUT_FOR_DELIVERY',
    NOW() - INTERVAL '3 hours',
    NOW() - INTERVAL '30 minutes'
) ON CONFLICT (tracking_number) DO UPDATE SET
    current_status = 'OUT_FOR_DELIVERY',
    assigned_courier_id = 'dd4480b8-1275-4c4c-b8ff-06f2bfff0558';

-- Status history for Parcel 1
INSERT INTO parcel_status_history (id, parcel_id, status, updated_by_user_id, delivery_note, created_at)
VALUES 
    (gen_random_uuid(), 'c1111111-1111-4111-8111-111111111111', 'CREATED', '8cc7c504-a54f-435e-b0f3-8c69af2713b1', 'Order created by merchant', NOW() - INTERVAL '3 hours'),
    (gen_random_uuid(), 'c1111111-1111-4111-8111-111111111111', 'ASSIGNED', 'b66123d7-96cc-4461-99aa-b40f786757ea', 'Assigned to Courier Kamal Perera', NOW() - INTERVAL '2 hours'),
    (gen_random_uuid(), 'c1111111-1111-4111-8111-111111111111', 'PICKED_UP', 'dd4480b8-1275-4c4c-b8ff-06f2bfff0558', 'Picked up from warehouse', NOW() - INTERVAL '90 minutes'),
    (gen_random_uuid(), 'c1111111-1111-4111-8111-111111111111', 'IN_TRANSIT', 'dd4480b8-1275-4c4c-b8ff-06f2bfff0558', 'Departed hub', NOW() - INTERVAL '60 minutes'),
    (gen_random_uuid(), 'c1111111-1111-4111-8111-111111111111', 'OUT_FOR_DELIVERY', 'dd4480b8-1275-4c4c-b8ff-06f2bfff0558', 'Out for final delivery in Kollupitiya', NOW() - INTERVAL '30 minutes');


-- 2. Parcel 2: OUT_FOR_DELIVERY (Ready for Doorstep Delivery or Failure test)
INSERT INTO parcels (
    id, tracking_number, sender_user_id, merchant_id, assigned_courier_id,
    origin_zone_id, destination_zone_id, pickup_address, dropoff_address,
    recipient_name, recipient_phone, weight_kg, parcel_size, estimated_fee,
    current_status, created_at, updated_at
) VALUES (
    'c2222222-2222-4222-8222-222222222222',
    'SD-20261114-001011',
    '8cc7c504-a54f-435e-b0f3-8c69af2713b1',
    '1d4376a6-e6ad-402d-8997-cb3432a35c89',
    'dd4480b8-1275-4c4c-b8ff-06f2bfff0558',
    1, 1,
    'Daraz Fulfillment Center, Peliyagoda',
    'Apartment 5B, Havelock City, Havelock Town, Colombo 05',
    'Dr. Sanath Jayasuriya',
    '+94773456789',
    3.20,
    'MEDIUM',
    606.00,
    'OUT_FOR_DELIVERY',
    NOW() - INTERVAL '4 hours',
    NOW() - INTERVAL '45 minutes'
) ON CONFLICT (tracking_number) DO UPDATE SET
    current_status = 'OUT_FOR_DELIVERY',
    assigned_courier_id = 'dd4480b8-1275-4c4c-b8ff-06f2bfff0558';

-- Status history for Parcel 2
INSERT INTO parcel_status_history (id, parcel_id, status, updated_by_user_id, delivery_note, created_at)
VALUES 
    (gen_random_uuid(), 'c2222222-2222-4222-8222-222222222222', 'CREATED', '8cc7c504-a54f-435e-b0f3-8c69af2713b1', 'Order created by merchant', NOW() - INTERVAL '4 hours'),
    (gen_random_uuid(), 'c2222222-2222-4222-8222-222222222222', 'ASSIGNED', 'b66123d7-96cc-4461-99aa-b40f786757ea', 'Assigned to Courier Kamal Perera', NOW() - INTERVAL '3 hours'),
    (gen_random_uuid(), 'c2222222-2222-4222-8222-222222222222', 'PICKED_UP', 'dd4480b8-1275-4c4c-b8ff-06f2bfff0558', 'Picked up from warehouse', NOW() - INTERVAL '2 hours'),
    (gen_random_uuid(), 'c2222222-2222-4222-8222-222222222222', 'IN_TRANSIT', 'dd4480b8-1275-4c4c-b8ff-06f2bfff0558', 'Departed hub', NOW() - INTERVAL '90 minutes'),
    (gen_random_uuid(), 'c2222222-2222-4222-8222-222222222222', 'OUT_FOR_DELIVERY', 'dd4480b8-1275-4c4c-b8ff-06f2bfff0558', 'Out for final delivery in Havelock City', NOW() - INTERVAL '45 minutes');


-- 3. Parcel 3: PICKED_UP (Ready for Transition to IN_TRANSIT)
INSERT INTO parcels (
    id, tracking_number, sender_user_id, merchant_id, assigned_courier_id,
    origin_zone_id, destination_zone_id, pickup_address, dropoff_address,
    recipient_name, recipient_phone, weight_kg, parcel_size, estimated_fee,
    current_status, created_at, updated_at
) VALUES (
    'c3333333-3333-4333-8333-333333333333',
    'SD-20261114-001012',
    '8cc7c504-a54f-435e-b0f3-8c69af2713b1',
    '1d4376a6-e6ad-402d-8997-cb3432a35c89',
    'dd4480b8-1275-4c4c-b8ff-06f2bfff0558',
    1, 1,
    'No. 15, Station Road, Bambalapitiya',
    'No. 88/2, High Level Road, Nugegoda',
    'Kavindu Wickramasinghe',
    '+94754567890',
    0.75,
    'SMALL',
    410.00,
    'PICKED_UP',
    NOW() - INTERVAL '2 hours',
    NOW() - INTERVAL '15 minutes'
) ON CONFLICT (tracking_number) DO UPDATE SET
    current_status = 'PICKED_UP',
    assigned_courier_id = 'dd4480b8-1275-4c4c-b8ff-06f2bfff0558';

-- Status history for Parcel 3
INSERT INTO parcel_status_history (id, parcel_id, status, updated_by_user_id, delivery_note, created_at)
VALUES 
    (gen_random_uuid(), 'c3333333-3333-4333-8333-333333333333', 'CREATED', '8cc7c504-a54f-435e-b0f3-8c69af2713b1', 'Order created by merchant', NOW() - INTERVAL '2 hours'),
    (gen_random_uuid(), 'c3333333-3333-4333-8333-333333333333', 'ASSIGNED', 'b66123d7-96cc-4461-99aa-b40f786757ea', 'Assigned to Courier Kamal Perera', NOW() - INTERVAL '1 hour'),
    (gen_random_uuid(), 'c3333333-3333-4333-8333-333333333333', 'PICKED_UP', 'dd4480b8-1275-4c4c-b8ff-06f2bfff0558', 'Picked up by Kamal', NOW() - INTERVAL '15 minutes');


-- 4. Parcel 4: ASSIGNED (Ready for "Pickup Parcel" transition)
INSERT INTO parcels (
    id, tracking_number, sender_user_id, merchant_id, assigned_courier_id,
    origin_zone_id, destination_zone_id, pickup_address, dropoff_address,
    recipient_name, recipient_phone, weight_kg, parcel_size, estimated_fee,
    current_status, created_at, updated_at
) VALUES (
    'c4444444-4444-4444-8444-444444444444',
    'SD-20261114-001013',
    '8cc7c504-a54f-435e-b0f3-8c69af2713b1',
    '1d4376a6-e6ad-402d-8997-cb3432a35c89',
    'dd4480b8-1275-4c4c-b8ff-06f2bfff0558',
    1, 1,
    'No. 200, Union Place, Colombo 02',
    'No. 12, Duplication Road, Colombo 04',
    'Anusha Fernando',
    '+94765678901',
    5.00,
    'LARGE',
    850.00,
    'ASSIGNED',
    NOW() - INTERVAL '1 hour',
    NOW() - INTERVAL '30 minutes'
) ON CONFLICT (tracking_number) DO UPDATE SET
    current_status = 'ASSIGNED',
    assigned_courier_id = 'dd4480b8-1275-4c4c-b8ff-06f2bfff0558';

-- Status history for Parcel 4
INSERT INTO parcel_status_history (id, parcel_id, status, updated_by_user_id, delivery_note, created_at)
VALUES 
    (gen_random_uuid(), 'c4444444-4444-4444-8444-444444444444', 'CREATED', '8cc7c504-a54f-435e-b0f3-8c69af2713b1', 'Order created by merchant', NOW() - INTERVAL '1 hour'),
    (gen_random_uuid(), 'c4444444-4444-4444-8444-444444444444', 'ASSIGNED', 'b66123d7-96cc-4461-99aa-b40f786757ea', 'Assigned to Courier Kamal Perera', NOW() - INTERVAL '30 minutes');

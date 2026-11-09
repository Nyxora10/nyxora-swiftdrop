package com.nyxora.swiftdrop.parcel.booking;

import com.nyxora.swiftdrop.parcel.booking.dto.BookingResponse;
import com.nyxora.swiftdrop.parcel.booking.dto.CustomerBookingRequest;
import com.nyxora.swiftdrop.parcel.pricing.ParcelQuoteRequest;
import com.nyxora.swiftdrop.parcel.pricing.ParcelQuoteResponse;

import java.util.UUID;

/**
 * Service contract for customer-facing parcel rate estimation and delivery booking operations.
 */
public interface CustomerBookingService {

    /**
     * Calculates an instant, itemized delivery fee quote via the dynamic pricing engine.
     *
     * @param request quote calculation parameters (origin, destination, weight, size, tier)
     * @return transparent fee breakdown with subtotal, zone surcharges, and discounts
     */
    ParcelQuoteResponse calculateQuote(ParcelQuoteRequest request);

    /**
     * Executes the end-to-end parcel delivery booking transaction:
     * 1. Validates zone activation and pricing rules.
     * 2. Computes the official fee quote.
     * 3. Generates a collision-free linear tracking number.
     * 4. Persists the parcel and creates the initial lifecycle audit history entry.
     * 5. Emits the {@code parcel.created} domain event via RabbitMQ.
     *
     * @param request      delivery and recipient specifications
     * @param senderUserId authenticated user UUID initiating the booking
     * @return confirmed booking details with tracking number and fee
     */
    BookingResponse bookParcel(CustomerBookingRequest request, UUID senderUserId);
}


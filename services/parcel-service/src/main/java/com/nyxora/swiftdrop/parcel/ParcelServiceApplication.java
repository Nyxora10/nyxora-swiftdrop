package com.nyxora.swiftdrop.parcel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SwiftDrop Parcel Service — Parcel Domain, Dynamic Pricing Strategy Engine
 * and Zone Administration microservice. Runs on port 8082.
 * Connects to parcel_db. All inbound requests are pre-authenticated
 * by the API Gateway (port 8080) which injects X-User-Id, X-User-Email,
 * and X-User-Role headers.
 */
@SpringBootApplication
public class ParcelServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParcelServiceApplication.class, args);
    }
}


package com.nyxora.swiftdrop.parcel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
        loadDotEnv();
        SpringApplication.run(ParcelServiceApplication.class, args);
    }

    private static void loadDotEnv() {
        Path[] possiblePaths = new Path[]{
                Paths.get(".env"),
                Paths.get("../../.env"),
                Paths.get("../.env")
        };

        for (Path path : possiblePaths) {
            if (Files.isRegularFile(path)) {
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                            continue;
                        }
                        int eqIdx = line.indexOf('=');
                        String key = line.substring(0, eqIdx).trim();
                        String val = line.substring(eqIdx + 1).trim();
                        if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        } else if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        }
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, val);
                        }
                    }
                    break;
                } catch (IOException ignored) {
                }
            }
        }
    }
}

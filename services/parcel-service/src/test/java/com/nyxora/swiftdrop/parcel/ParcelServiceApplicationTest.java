package com.nyxora.swiftdrop.parcel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("test")
class ParcelServiceApplicationTest {

    @Test
    @DisplayName("Context smoke test: Application context should bootstrap cleanly with all beans")
    void contextLoads() {
        // Sanity check verifying that the Spring context, WebSocket infrastructure, and JPA repositories load
    }

    @Test
    @DisplayName("Main entry point should execute cleanly")
    void mainStartsSuccessfully() {
        assertDoesNotThrow(() ->
                ParcelServiceApplication.main(new String[]{
                        "--spring.profiles.active=test",
                        "--server.port=0"
                })
        );
    }
}


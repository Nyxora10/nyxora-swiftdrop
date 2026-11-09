package com.nyxora.swiftdrop.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceApplicationTest {

    @Test
    void contextLoads() {
        // Sanity check verifying that the Spring ApplicationContext starts cleanly
    }

    @Test
    void mainStartsSuccessfully() {
        assertDoesNotThrow(() ->
                NotificationServiceApplication.main(new String[]{
                        "--spring.profiles.active=test",
                        "--server.port=0"
                })
        );
    }
}


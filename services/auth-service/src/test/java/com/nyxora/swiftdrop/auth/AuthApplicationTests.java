package com.nyxora.swiftdrop.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AuthApplicationTests {

    @Test
    @DisplayName("Context loads successfully")
    void contextLoads() {
        // Basic sanity check that application context can start
    }

    @Test
    @DisplayName("AuthApplication constructor and loadDotEnv branch coverage")
    void testConstructorAndLoadDotEnv() throws Exception {
        AuthApplication app = new AuthApplication();
        assertNotNull(app);

        Path envPath = Paths.get(".env");
        boolean existed = Files.exists(envPath);
        byte[] backup = existed ? Files.readAllBytes(envPath) : null;

        try {
            List<String> lines = List.of(
                    "# Comment line",
                    "",
                    "INVALID_LINE_NO_EQUALS",
                    "TEST_DOUBLE_QUOTED=\"double_val\"",
                    "TEST_SINGLE_QUOTED='single_val'",
                    "TEST_UNQUOTED=unquoted_val",
                    "TEST_ALREADY_SET=preset_val"
            );
            System.setProperty("TEST_ALREADY_SET", "initial");
            Files.write(envPath, lines);

            Method method = AuthApplication.class.getDeclaredMethod("loadDotEnv");
            method.setAccessible(true);
            method.invoke(null);

            assertEquals("double_val", System.getProperty("TEST_DOUBLE_QUOTED"));
            assertEquals("single_val", System.getProperty("TEST_SINGLE_QUOTED"));
            assertEquals("unquoted_val", System.getProperty("TEST_UNQUOTED"));
            assertEquals("initial", System.getProperty("TEST_ALREADY_SET"));
        } finally {
            System.clearProperty("TEST_DOUBLE_QUOTED");
            System.clearProperty("TEST_SINGLE_QUOTED");
            System.clearProperty("TEST_UNQUOTED");
            System.clearProperty("TEST_ALREADY_SET");
            if (existed && backup != null) {
                Files.write(envPath, backup);
            } else {
                Files.deleteIfExists(envPath);
            }
        }
    }
}


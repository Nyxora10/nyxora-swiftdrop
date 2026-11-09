package com.nyxora.swiftdrop.ai_assistant_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication
public class AiAssistantServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantServiceApplication.class);

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(AiAssistantServiceApplication.class, args);
    }

    /**
     * Automatically loads .env file into System properties so Spring Boot resolves
     * ${GEMINI_API_KEY}, ${GEMINI_MODEL}, and other environment variables seamlessly.
     */
    private static void loadDotEnv() {
        List<Path> candidatePaths = List.of(
                Path.of(".env"),
                Path.of("services/ai-assistant-service/.env"),
                Path.of("../.env"),
                Path.of("../../.env")
        );

        for (Path path : candidatePaths) {
            if (Files.exists(path)) {
                try {
                    List<String> lines = Files.readAllLines(path);
                    int loadedCount = 0;
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                            int idx = trimmed.indexOf('=');
                            String key = trimmed.substring(0, idx).trim();
                            String value = trimmed.substring(idx + 1).trim();

                            // Strip quotes if present
                            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                                (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }

                            // Do not override existing system env/properties
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                                loadedCount++;
                            }
                        }
                    }
                    log.info("Successfully loaded {} variables from .env file: {}", loadedCount, path.toAbsolutePath());
                    break;
                } catch (IOException ex) {
                    log.warn("Could not read .env file at {}: {}", path, ex.getMessage());
                }
            }
        }
    }
}


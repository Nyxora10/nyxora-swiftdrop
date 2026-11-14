package com.nyxora.swiftdrop.ai_assistant_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiAssistantServiceApplicationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should instantiate application class")
    void shouldInstantiateApplication() {
        AiAssistantServiceApplication app = new AiAssistantServiceApplication();
        assertThat(app).isNotNull();
    }

    @Test
    @DisplayName("Should invoke default loadDotEnv without failure")
    void shouldInvokeDefaultLoadDotEnv() {
        AiAssistantServiceApplication.loadDotEnv();
    }

    @Test
    @DisplayName("Should load environment variables from file with comments, quotes, and duplicates")
    void shouldLoadDotEnvFromPaths() throws IOException {
        Path envFile = tempDir.resolve(".env");
        List<String> lines = List.of(
                "",
                "   ",
                "# This is a comment",
                "INVALID_LINE_WITHOUT_EQUALS",
                "TEST_VAR_PLAIN=hello_world",
                "TEST_VAR_DOUBLE_QUOTES=\"quoted_double\"",
                "TEST_VAR_SINGLE_QUOTES='quoted_single'",
                "TEST_VAR_EXISTING=new_value"
        );
        Files.write(envFile, lines);

        System.setProperty("TEST_VAR_EXISTING", "original_value");

        AiAssistantServiceApplication.loadDotEnvFromPaths(List.of(
                tempDir.resolve("non_existent_file"),
                envFile
        ));

        assertThat(System.getProperty("TEST_VAR_PLAIN")).isEqualTo("hello_world");
        assertThat(System.getProperty("TEST_VAR_DOUBLE_QUOTES")).isEqualTo("quoted_double");
        assertThat(System.getProperty("TEST_VAR_SINGLE_QUOTES")).isEqualTo("quoted_single");
        assertThat(System.getProperty("TEST_VAR_EXISTING")).isEqualTo("original_value");

        // Clean up
        System.clearProperty("TEST_VAR_PLAIN");
        System.clearProperty("TEST_VAR_DOUBLE_QUOTES");
        System.clearProperty("TEST_VAR_SINGLE_QUOTES");
        System.clearProperty("TEST_VAR_EXISTING");
    }

    @Test
    @DisplayName("Should handle IOException gracefully when reading candidate path")
    void shouldHandleIoExceptionGracefully() {
        // Passing a directory as a candidate path triggers IOException on Files.readAllLines
        AiAssistantServiceApplication.loadDotEnvFromPaths(List.of(tempDir));
    }
}

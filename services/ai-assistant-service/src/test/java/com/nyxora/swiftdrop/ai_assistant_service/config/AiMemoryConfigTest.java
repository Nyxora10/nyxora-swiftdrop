package com.nyxora.swiftdrop.ai_assistant_service.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiMemoryConfigTest {

    private AiMemoryConfig aiMemoryConfig;
    private ChatMemoryRepository chatMemoryRepository;
    private ChatMemory chatMemory;

    @BeforeEach
    void setUp() {
        aiMemoryConfig = new AiMemoryConfig();
        ReflectionTestUtils.setField(aiMemoryConfig, "maxMessages", 20);

        chatMemoryRepository = aiMemoryConfig.chatMemoryRepository();
        chatMemory = aiMemoryConfig.chatMemory(chatMemoryRepository);
    }

    @Test
    @DisplayName("Should initialize InMemoryChatMemoryRepository and MessageWindowChatMemory")
    void shouldInitializeBeans() {
        assertThat(chatMemoryRepository).isNotNull();
        assertThat(chatMemoryRepository).isInstanceOf(InMemoryChatMemoryRepository.class);
        assertThat(chatMemory).isNotNull();
    }

    @Test
    @DisplayName("Should isolate conversations by conversationId")
    void shouldIsolateConversationsByConversationId() {
        String conv1 = "conv-1";
        String conv2 = "conv-2";

        chatMemory.add(conv1, List.of(new UserMessage("Message for conv 1")));
        chatMemory.add(conv2, List.of(new UserMessage("Message for conv 2")));

        List<Message> conv1Messages = chatMemory.get(conv1);
        List<Message> conv2Messages = chatMemory.get(conv2);

        assertThat(conv1Messages).hasSize(1);
        assertThat(conv1Messages.get(0).getText()).isEqualTo("Message for conv 1");

        assertThat(conv2Messages).hasSize(1);
        assertThat(conv2Messages.get(0).getText()).isEqualTo("Message for conv 2");
    }

    @Test
    @DisplayName("Should enforce sliding window of 20 messages")
    void shouldEnforceSlidingWindowLimit() {
        String conversationId = "conv-window-test";

        for (int i = 1; i <= 25; i++) {
            chatMemory.add(conversationId, List.of(
                    new UserMessage("User message " + i),
                    new AssistantMessage("Assistant reply " + i)
            ));
        }

        List<Message> retainedMessages = chatMemory.get(conversationId);

        // Window size is 20, so at most 20 messages should be retained
        assertThat(retainedMessages).hasSize(20);

        // Verify that the oldest messages were evicted and the most recent are retained
        Message lastMessage = retainedMessages.get(retainedMessages.size() - 1);
        assertThat(lastMessage.getText()).isEqualTo("Assistant reply 25");
    }
}


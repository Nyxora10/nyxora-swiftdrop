package com.nyxora.swiftdrop.parcel.websocket.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nyxora.swiftdrop.parcel.websocket.broadcaster.ParcelWebSocketBroadcaster;
import com.nyxora.swiftdrop.parcel.websocket.dto.ParcelTrackingUpdateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-End Live STOMP Client Integration Test.
 *
 * Spawns an embedded Tomcat container on a random port and connects a real
 * {@link WebSocketStompClient} over SockJS to {@code /ws-swiftdrop}.
 * Verifies that subscribing to {@code /topic/parcels/{trackingNumber}}
 * and dispatching via {@link ParcelWebSocketBroadcaster} delivers the message over the network.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StompWebSocketClientIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ParcelWebSocketBroadcaster broadcaster;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        List<Transport> transports = Collections.singletonList(
                new WebSocketTransport(new StandardWebSocketClient())
        );
        SockJsClient sockJsClient = new SockJsClient(transports);
        stompClient = new WebSocketStompClient(sockJsClient);

        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        converter.setObjectMapper(mapper);
        stompClient.setMessageConverter(converter);
    }

    @Test
    @DisplayName("STOMP Over Network: Client connects to /ws-swiftdrop, subscribes to topic, and receives broadcast update")
    void shouldConnectAndReceiveBroadcastOverStomp() throws Exception {
        String wsUrl = "ws://localhost:" + port + "/ws-swiftdrop";
        String trackingNumber = "SWIFT-STOMP-CLIENT-001";
        String topicDestination = "/topic/parcels/" + trackingNumber;

        CompletableFuture<ParcelTrackingUpdateDTO> messageFuture = new CompletableFuture<>();

        StompSession session = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        assertNotNull(session);

        session.subscribe(topicDestination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ParcelTrackingUpdateDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof ParcelTrackingUpdateDTO update) {
                    messageFuture.complete(update);
                }
            }
        });

        // Small delay to allow subscription frame to register with in-memory broker
        Thread.sleep(200);

        ParcelTrackingUpdateDTO broadcastPayload = ParcelTrackingUpdateDTO.builder()
                .trackingNumber(trackingNumber)
                .currentStatus("OUT_FOR_DELIVERY")
                .statusDescription("Assigned courier is on the way")
                .currentZone("Colombo Zone 04")
                .recipientNameMasked("N***l P***a")
                .deliveryNote("Leave at reception")
                .proofPhotoUrl(null)
                .timestamp(Instant.now())
                .build();

        broadcaster.broadcastStatusUpdate(broadcastPayload);

        ParcelTrackingUpdateDTO received = messageFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(received);
        assertEquals(trackingNumber, received.getTrackingNumber());
        assertEquals("OUT_FOR_DELIVERY", received.getCurrentStatus());
        assertEquals("N***l P***a", received.getRecipientNameMasked());
        assertEquals("Colombo Zone 04", received.getCurrentZone());

        session.disconnect();
    }
}


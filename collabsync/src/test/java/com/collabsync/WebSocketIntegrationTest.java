package com.collabsync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private WebSocketStompClient stompClient;
    private String accessToken;
    private int serverPort;

    @Test
    void websocketConnect_withValidToken_establishesConnection() throws Exception {
        // Register and login to get token
        accessToken = getAccessToken();

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompSession session = stompClient.connect(
                "ws://localhost:{port}/ws",
                new StompSessionHandlerAdapter() {},
                serverPort
        ).get(5, TimeUnit.SECONDS);

        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    void websocketConnect_withoutToken_rejectsConnection() throws Exception {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        stompClient.connect(
                "ws://localhost:{port}/ws",
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(StompSession s, StompCommand c, StompHeaders h, byte[] p, Throwable ex) {
                        error.set(ex);
                        latch.countDown();
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        error.set(exception);
                        latch.countDown();
                    }
                },
                serverPort
        );

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isNotNull();
    }

    @Test
    void chatSend_andReceive_broadcastsToAllSubscribers() throws Exception {
        accessToken = getAccessToken();

        // Create workspace and chat room via REST
        UUID workspaceId = createWorkspace("Test Workspace");
        UUID roomId = createChatRoom(workspaceId, "General");

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        // Connect two sessions
        StompSession session1 = connectStomp(accessToken);
        StompSession session2 = connectStomp(getSecondUserToken());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();

        // Subscribe session2 to chat topic
        session2.subscribe("/topic/chat/" + roomId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedMessage.set((String) payload);
                latch.countDown();
            }
        });

        // Send message from session1
        String messagePayload = """
            {"type":"CHAT_MESSAGE","roomId":"%s","content":"Hello World"}
            """.formatted(roomId);
        session1.send("/app/chat/" + roomId + "/send", messagePayload);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedMessage.get()).contains("Hello World");

        session1.disconnect();
        session2.disconnect();
    }

    @Test
    void documentEdit_broadcastsToCollaborators() throws Exception {
        accessToken = getAccessToken();

        UUID workspaceId = createWorkspace("Test Workspace");
        UUID documentId = createDocument(workspaceId, "Test Doc");

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompSession session1 = connectStomp(accessToken);
        StompSession session2 = connectStomp(getSecondUserToken());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedOp = new AtomicReference<>();

        session2.subscribe("/topic/doc/" + documentId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedOp.set((String) payload);
                latch.countDown();
            }
        });

        // Send edit operation
        String opPayload = """
            {"type":"DOC_OP","documentId":"%s","payload":{"opType":"INSERT","position":0,"content":"Hello"},"clientSeq":1}
            """.formatted(documentId);
        session1.send("/app/doc/" + documentId + "/edit", opPayload);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedOp.get()).contains("INSERT");

        session1.disconnect();
        session2.disconnect();
    }

    private String getAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"email\":\"wsuser" + System.currentTimeMillis() + "@example.com\",\"password\":\"password123\",\"displayName\":\"WS User\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/register", entity, String.class
        );
        // Simple extraction of access token
        String responseBody = response.getBody();
        int idx = responseBody.indexOf("\"accessToken\":\"") + 15;
        return responseBody.substring(idx, responseBody.indexOf("\"", idx));
    }

    private String getSecondUserToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"email\":\"wsuser2" + System.currentTimeMillis() + "@example.com\",\"password\":\"password123\",\"displayName\":\"WS User 2\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/register", entity, String.class
        );
        String responseBody = response.getBody();
        int idx = responseBody.indexOf("\"accessToken\":\"") + 15;
        return responseBody.substring(idx, responseBody.indexOf("\"", idx));
    }

    private UUID createWorkspace(String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"name\":\"" + name + "\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/workspaces", entity, String.class
        );
        // Extract UUID from response
        return UUID.fromString(response.getBody().split("\"id\":\"")[1].split("\"")[0]);
    }

    private UUID createChatRoom(UUID workspaceId, String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"workspaceId\":\"" + workspaceId + "\",\"name\":\"" + name + "\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/chat/rooms", entity, String.class
        );
        return UUID.fromString(response.getBody().split("\"id\":\"")[1].split("\"")[0]);
    }

    private UUID createDocument(UUID workspaceId, String title) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"workspaceId\":\"" + workspaceId + "\",\"title\":\"" + title + "\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/documents", entity, String.class
        );
        return UUID.fromString(response.getBody().split("\"id\":\"")[1].split("\"")[0]);
    }

    private StompSession connectStomp(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        return stompClient.connect(
                "ws://localhost:{port}/ws",
                new StompSessionHandlerAdapter() {},
                serverPort
        ).get(5, TimeUnit.SECONDS);
    }
}
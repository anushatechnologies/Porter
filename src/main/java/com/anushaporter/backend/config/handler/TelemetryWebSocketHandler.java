package com.anushaporter.backend.config.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Handles real-time telemetry stream for admin live map.
 * Emits driver:telemetry and order:update events.
 */
@Component
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public TelemetryWebSocketHandler() {
        // Broadcast periodic driver telemetry to connected admin sessions
        executorService.scheduleAtFixedRate(this::broadcastTelemetry, 3, 3, TimeUnit.SECONDS);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        session.sendMessage(new TextMessage("{\"event\":\"connection:established\",\"message\":\"Connected to Anusha Porter Telemetry Stream\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        // Echo back ping or handle incoming driver telemetry
        if (payload.contains("ping")) {
            session.sendMessage(new TextMessage("{\"event\":\"pong\",\"timestamp\":" + System.currentTimeMillis() + "}"));
        } else {
            // Broadcast telemetry update to all connected clients
            broadcastMessage("{\"event\":\"driver:telemetry\",\"payload\":" + payload + "}");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
    }

    private void broadcastTelemetry() {
        if (sessions.isEmpty()) return;

        // Sample live GPS payload for active drivers on admin live map
        String json = "{"
                + "\"event\":\"driver:telemetry\","
                + "\"data\":{"
                + "\"driverId\":\"DRV-102\","
                + "\"location\":{\"lat\":17.4483,\"lng\":78.3915,\"speed\":24.5,\"angle\":180},"
                + "\"timestamp\":" + System.currentTimeMillis()
                + "}}";

        broadcastMessage(json);
    }

    public void broadcastOrderUpdate(String orderId, String status) {
        String json = "{"
                + "\"event\":\"order:update\","
                + "\"data\":{"
                + "\"orderId\":\"" + orderId + "\","
                + "\"status\":\"" + status + "\","
                + "\"timestamp\":" + System.currentTimeMillis()
                + "}}";
        broadcastMessage(json);
    }

    private void broadcastMessage(String json) {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (IOException ignored) {}
            }
        }
    }
}

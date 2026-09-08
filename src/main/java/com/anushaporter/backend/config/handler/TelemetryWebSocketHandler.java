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
                + "\"driverId\":\"DRV-104\","
                + "\"location\":{\"lat\":17.4375,\"lng\":78.3780,\"speed\":0.0,\"angle\":45},"
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

    public void broadcastOfferNew(String bookingId, String jsonPayload) {
        broadcastOfferNew(bookingId, jsonPayload, null);
    }

    public void broadcastOfferNew(String bookingId, String jsonPayload, java.util.List<Long> targetDriverIds) {
        String driverIdsJson = targetDriverIds != null && !targetDriverIds.isEmpty()
                ? "[" + targetDriverIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "]"
                : "[]";

        String json = "{"
                + "\"event\":\"driver:offer:new\","
                + "\"bookingId\":\"" + (bookingId != null ? bookingId : "") + "\","
                + "\"targetDriverIds\":" + driverIdsJson + ","
                + "\"data\":" + (jsonPayload != null ? jsonPayload : "{}") + ","
                + "\"timestamp\":" + System.currentTimeMillis()
                + "}";
        broadcastMessage(json);
    }

    public void broadcastOfferDismiss(String bookingId, String reason) {
        broadcastOfferDismiss(bookingId, reason, null);
    }

    public void broadcastOfferDismiss(String bookingId, String reason, Long winningDriverId) {
        String json = "{"
                + "\"event\":\"driver:offer:stop\","
                + "\"bookingId\":\"" + (bookingId != null ? bookingId : "") + "\","
                + "\"winningDriverId\":" + (winningDriverId != null ? winningDriverId : "null") + ","
                + "\"stopSound\":true,"
                + "\"action\":\"STOP_RINGTONE\","
                + "\"reason\":\"" + (reason != null ? reason : "ACCEPTED") + "\","
                + "\"message\":\"Order offer has been closed. Stop ringtone immediately.\","
                + "\"timestamp\":" + System.currentTimeMillis()
                + "}";
        broadcastMessage(json);
    }

    public void broadcastOfferDismissForDriver(String bookingId, Long driverId, String reason) {
        String json = "{"
                + "\"event\":\"driver:offer:stop\","
                + "\"bookingId\":\"" + (bookingId != null ? bookingId : "") + "\","
                + "\"targetDriverId\":" + (driverId != null ? driverId : "null") + ","
                + "\"driverId\":" + (driverId != null ? driverId : "null") + ","
                + "\"stopSound\":true,"
                + "\"action\":\"STOP_RINGTONE\","
                + "\"reason\":\"" + (reason != null ? reason : "REJECTED_BY_DRIVER") + "\","
                + "\"message\":\"Order offer rejected by driver. Stop ringtone immediately.\","
                + "\"timestamp\":" + System.currentTimeMillis()
                + "}";
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

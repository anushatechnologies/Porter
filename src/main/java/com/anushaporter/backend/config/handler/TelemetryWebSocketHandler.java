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
    private final java.util.Map<String, java.util.Set<WebSocketSession>> bookingSubscriptions = new java.util.concurrent.ConcurrentHashMap<>();
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public TelemetryWebSocketHandler() {
        // Broadcast periodic driver telemetry to connected admin sessions and subscribed passenger apps
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
        if (payload == null || payload.isBlank()) return;

        if (payload.contains("ping")) {
            session.sendMessage(new TextMessage("{\"event\":\"pong\",\"timestamp\":" + System.currentTimeMillis() + "}"));
            return;
        }

        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(payload);
            String type = node.has("type") ? node.get("type").asText() : "";

            if ("SUBSCRIBE_BOOKING".equalsIgnoreCase(type) || "SUBSCRIBE_ORDER".equalsIgnoreCase(type)) {
                String bookingId = node.has("bookingId") ? node.get("bookingId").asText()
                        : (node.has("orderId") ? node.get("orderId").asText() : "");
                if (!bookingId.isBlank()) {
                    bookingSubscriptions.computeIfAbsent(bookingId, k -> java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>())).add(session);
                    session.sendMessage(new TextMessage("{\"type\":\"SUBSCRIPTION_CONFIRMED\",\"bookingId\":\"" + bookingId + "\"}"));
                }
                return;
            } else if ("UNSUBSCRIBE_BOOKING".equalsIgnoreCase(type)) {
                String bookingId = node.has("bookingId") ? node.get("bookingId").asText() : "";
                if (!bookingId.isBlank() && bookingSubscriptions.containsKey(bookingId)) {
                    bookingSubscriptions.get(bookingId).remove(session);
                }
                return;
            }
        } catch (Exception ignored) {
        }

        // Broadcast telemetry update to all connected clients
        broadcastMessage("{\"event\":\"driver:telemetry\",\"payload\":" + payload + "}");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        for (java.util.Set<WebSocketSession> subs : bookingSubscriptions.values()) {
            subs.remove(session);
        }
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

        // Emit live DRIVER_LOCATION updates for subscribed passenger rides
        if (!bookingSubscriptions.isEmpty()) {
            for (java.util.Map.Entry<String, java.util.Set<WebSocketSession>> entry : bookingSubscriptions.entrySet()) {
                String bId = entry.getKey();
                java.util.Set<WebSocketSession> subs = entry.getValue();
                if (subs != null && !subs.isEmpty()) {
                    double lat = 17.4420 + (Math.random() * 0.003 - 0.0015);
                    double lng = 78.3510 + (Math.random() * 0.003 - 0.0015);
                    int heading = (int) (Math.random() * 360);
                    String locJson = "{"
                            + "\"type\":\"DRIVER_LOCATION\","
                            + "\"bookingId\":\"" + bId + "\","
                            + "\"lat\":" + String.format(java.util.Locale.US, "%.5f", lat) + ","
                            + "\"lng\":" + String.format(java.util.Locale.US, "%.5f", lng) + ","
                            + "\"heading\":" + heading
                            + "}";
                    for (WebSocketSession s : subs) {
                        if (s.isOpen()) {
                            try {
                                s.sendMessage(new TextMessage(locJson));
                            } catch (IOException ignored) {}
                        }
                    }
                }
            }
        }
    }

    public void broadcastDriverLocation(String bookingId, double lat, double lng, int heading) {
        String json = "{"
                + "\"type\":\"DRIVER_LOCATION\","
                + "\"bookingId\":\"" + bookingId + "\","
                + "\"lat\":" + lat + ","
                + "\"lng\":" + lng + ","
                + "\"heading\":" + heading
                + "}";
        broadcastMessage(json);
    }

    public void broadcastPassengerStatus(String bookingId, String status) {
        String json = "{"
                + "\"type\":\"ORDER_STATUS_UPDATED\","
                + "\"bookingId\":\"" + bookingId + "\","
                + "\"status\":\"" + status + "\""
                + "}";
        broadcastMessage(json);
        broadcastOrderUpdate(bookingId, status);
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

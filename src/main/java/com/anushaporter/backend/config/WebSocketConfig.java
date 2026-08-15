package com.anushaporter.backend.config;

import com.anushaporter.backend.config.handler.TelemetryWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(telemetryWebSocketHandler(), "/ws", "/ws/telemetry", "/ws/tracking")
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public TelemetryWebSocketHandler telemetryWebSocketHandler() {
        return new TelemetryWebSocketHandler();
    }
}

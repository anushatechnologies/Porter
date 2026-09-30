package com.anushaporter.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OsrmRoutingService
 *
 * Provides real navigable road distance and duration using the 100% FREE Open Source
 * Routing Machine (OSRM) service with intelligent caching and seamless Haversine fallback.
 */
@Service
@Slf4j
public class OsrmRoutingService {

    @Value("${osrm.routing.url:https://router.project-osrm.org}")
    private String osrmBaseUrl;

    @Autowired
    private ObjectMapper objectMapper;

    private final RestTemplate restTemplate;

    // Cache storing routeKey -> CacheEntry with 30-minute expiration
    private final Map<String, CacheEntry> routeCache = new ConcurrentHashMap<>();

    public OsrmRoutingService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    public static class RouteDetails {
        private final double distanceKm;
        private final int distanceMeters;
        private final int durationSeconds;
        private final boolean fallback;

        public RouteDetails(double distanceKm, int distanceMeters, int durationSeconds, boolean fallback) {
            this.distanceKm = distanceKm;
            this.distanceMeters = distanceMeters;
            this.durationSeconds = durationSeconds;
            this.fallback = fallback;
        }

        public double getDistanceKm() { return distanceKm; }
        public int getDistanceMeters() { return distanceMeters; }
        public int getDurationSeconds() { return durationSeconds; }
        public boolean isFallback() { return fallback; }

        @Override
        public String toString() {
            return "RouteDetails{" +
                    "distanceKm=" + distanceKm +
                    ", distanceMeters=" + distanceMeters +
                    ", durationSeconds=" + durationSeconds +
                    ", fallback=" + fallback +
                    '}';
        }
    }

    private static class CacheEntry {
        final RouteDetails details;
        final long expiresAt;

        CacheEntry(RouteDetails details, long ttlMillis) {
            this.details = details;
            this.expiresAt = System.currentTimeMillis() + ttlMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /**
     * Computes real road route metrics between pickup and drop locations.
     */
    public RouteDetails calculateRoute(Double pickupLat, Double pickupLng, Double dropLat, Double dropLng) {
        if (pickupLat == null || pickupLng == null || dropLat == null || dropLng == null) {
            return new RouteDetails(5.0, 5000, 900, true);
        }

        // If origin and destination are virtually identical (< 15 meters)
        if (Math.abs(pickupLat - dropLat) < 0.0001 && Math.abs(pickupLng - dropLng) < 0.0001) {
            return new RouteDetails(1.0, 1000, 180, true);
        }

        // 1. Check in-memory Cache
        String cacheKey = buildCacheKey(pickupLat, pickupLng, dropLat, dropLng);
        CacheEntry cached = routeCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.details;
        }

        // 2. Query OSRM Driving Profile
        try {
            String baseUrl = (osrmBaseUrl != null && !osrmBaseUrl.isBlank())
                    ? osrmBaseUrl.replaceAll("/+$", "")
                    : "https://router.project-osrm.org";

            // OSRM expects coordinates in {lon},{lat} order
            String url = String.format(Locale.US,
                    "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false&alternatives=false&steps=false",
                    baseUrl, pickupLng, pickupLat, dropLng, dropLat);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "PorterDeliveryPlatform/1.0 (contact@anushaporter.com)");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String code = root.path("code").asText();

                if ("Ok".equalsIgnoreCase(code)) {
                    JsonNode routes = root.path("routes");
                    if (routes.isArray() && routes.size() > 0) {
                        JsonNode firstRoute = routes.get(0);
                        double distMeters = firstRoute.path("distance").asDouble(0.0);
                        double durSeconds = firstRoute.path("duration").asDouble(0.0);

                        double distKm = Math.max(0.5, Math.round((distMeters / 1000.0) * 10.0) / 10.0);
                        int meters = (int) Math.round(distMeters);
                        int seconds = Math.max(60, (int) Math.round(durSeconds));

                        RouteDetails result = new RouteDetails(distKm, meters, seconds, false);
                        routeCache.put(cacheKey, new CacheEntry(result, 30 * 60 * 1000L)); // 30 min cache
                        log.info("OSRM road route calculated: {} km ({} meters, {}s) for {},{} -> {},{}",
                                distKm, meters, seconds, pickupLat, pickupLng, dropLat, dropLng);
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("OSRM routing call failed ({}), falling back to intelligent road estimation: {}",
                    e.getClass().getSimpleName(), e.getMessage());
        }

        // 3. Fallback: Haversine with realistic Indian urban road network curvature factor (1.40x)
        RouteDetails fallback = calculateFallback(pickupLat, pickupLng, dropLat, dropLng);
        routeCache.put(cacheKey, new CacheEntry(fallback, 5 * 60 * 1000L)); // 5 min cache for fallback
        return fallback;
    }

    public RouteDetails calculateFallback(double lat1, double lon1, double lat2, double lon2) {
        double directKm = calculateHaversineDistanceKm(lat1, lon1, lat2, lon2);
        // Urban road winding ratio in metros like Hyderabad is ~1.40x
        double roadKm = Math.max(1.0, Math.round((directKm * 1.40) * 10.0) / 10.0);
        int meters = (int) Math.round(roadKm * 1000.0);
        // ~2.4 mins per km in city traffic
        int seconds = Math.max(120, (int) Math.round(roadKm * 144.0));
        return new RouteDetails(roadKm, meters, seconds, true);
    }

    public static double calculateHaversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private String buildCacheKey(double lat1, double lon1, double lat2, double lon2) {
        long l1 = Math.round(lat1 * 1000.0);
        long n1 = Math.round(lon1 * 1000.0);
        long l2 = Math.round(lat2 * 1000.0);
        long n2 = Math.round(lon2 * 1000.0);
        return l1 + "," + n1 + ";" + l2 + "," + n2;
    }
}

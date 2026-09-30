package com.anushaporter.backend;

import com.anushaporter.backend.service.OsrmRoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(OsrmRoutingIntegrationTest.TestConfig.class)
public class OsrmRoutingIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public S3Client mockS3Client() {
            return Mockito.mock(S3Client.class);
        }

        @Bean
        @Primary
        public S3Presigner mockS3Presigner() {
            return Mockito.mock(S3Presigner.class);
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private OsrmRoutingService osrmRoutingService;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    public void testOsrmRoutingServiceCalculatesRoadMetrics() {
        assertNotNull(osrmRoutingService, "OsrmRoutingService must be injected");

        // Gachibowli to Raidurg Metro coordinates
        double pLat = 17.4474;
        double pLng = 78.3565;
        double dLat = 17.4380;
        double dLng = 78.3780;

        OsrmRoutingService.RouteDetails route = osrmRoutingService.calculateRoute(pLat, pLng, dLat, dLng);

        assertNotNull(route);
        assertTrue(route.getDistanceKm() > 0.0, "DistanceKm must be positive");
        assertTrue(route.getDistanceMeters() > 0, "DistanceMeters must be positive");
        assertTrue(route.getDurationSeconds() > 0, "DurationSeconds must be positive");

        // Second call should return cached details
        OsrmRoutingService.RouteDetails cached = osrmRoutingService.calculateRoute(pLat, pLng, dLat, dLng);
        assertEquals(route.getDistanceKm(), cached.getDistanceKm(), 0.001);
        assertEquals(route.getDistanceMeters(), cached.getDistanceMeters());
    }

    @Test
    public void testFallbackCalculationAppliesRoadFactor() {
        double pLat = 17.4474;
        double pLng = 78.3565;
        double dLat = 17.4380;
        double dLng = 78.3780;

        OsrmRoutingService.RouteDetails fallback = osrmRoutingService.calculateFallback(pLat, pLng, dLat, dLng);
        assertNotNull(fallback);
        assertTrue(fallback.isFallback());
        assertTrue(fallback.getDistanceKm() >= 1.0);
        assertTrue(fallback.getDistanceMeters() >= 1000);
        assertTrue(fallback.getDurationSeconds() >= 120);
    }

    @Test
    public void testPricingEstimateReturnsDistanceMetersAndDuration() throws Exception {
        String body = """
            {
                "pickupLat": 17.4474,
                "pickupLng": 78.3565,
                "dropLat": 17.4380,
                "dropLng": 78.3780,
                "vehicleId": "two-wheeler"
            }
        """;

        mockMvc.perform(post("/api/pricing/estimate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.distanceKm").isNumber())
                .andExpect(jsonPath("$.distanceMeters").isNumber())
                .andExpect(jsonPath("$.durationSeconds").isNumber())
                .andExpect(jsonPath("$.totalFare").isNumber());
    }
}

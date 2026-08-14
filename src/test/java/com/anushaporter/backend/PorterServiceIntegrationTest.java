package com.anushaporter.backend;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.PorterService;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.PorterServiceRepository;
import com.anushaporter.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(PorterServiceIntegrationTest.TestConfig.class)
public class PorterServiceIntegrationTest {

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
    private PorterServiceRepository serviceRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String adminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        serviceRepository.deleteAll();
        userRepository.deleteAll();

        // Create Admin user
        AppUser admin = new AppUser();
        admin.setEmail("admin@porter.com");
        admin.setName("Super Admin");
        admin.setRole("ADMIN");
        admin.setPhone("9999999999");
        userRepository.save(admin);

        adminToken = jwtUtil.generateToken(admin.getEmail());
    }

    @Test
    void testAdminCreateAndListServices() throws Exception {
        String payload = """
        {
            "serviceId": "tata-ace-750kg",
            "name": "Tata Ace (Chota Hathi)",
            "label": "Tata Ace",
            "category": "vehicle",
            "subtitle": "Ideal for 1-2 BHK house shifting or small businesses",
            "baseFare": 249.0,
            "baseKm": 2.0,
            "perKmRate": 22.50,
            "helperRate": 300.0,
            "capacityKg": 750,
            "capacityLabel": "750 Kg",
            "dimensions": "{\\"length\\": \\"7 ft\\", \\"width\\": \\"4.5 ft\\", \\"height\\": \\"5 ft\\"}",
            "etaLabel": "10-15 mins",
            "iconUrl": "https://cdn.anushaporter.com/services/tata-ace.png",
            "bgTint": "#EEF4FF",
            "isActive": true,
            "displayOrder": 1,
            "availableCities": "[\\"Hyderabad\\", \\"Secunderabad\\"]"
        }
        """;

        // 1. Create service via Admin API
        mockMvc.perform(post("/api/admin/services")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.service.serviceId", is("tata-ace-750kg")))
                .andExpect(jsonPath("$.service.baseFare", is(249.0)))
                .andExpect(jsonPath("$.service.capacityKg", is(750)));

        // 2. Fetch list via Admin API
        mockMvc.perform(get("/api/admin/services")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.count", is(1)))
                .andExpect(jsonPath("$.services[0].name", is("Tata Ace (Chota Hathi)")));

        // 3. Fetch single service by slug
        mockMvc.perform(get("/api/admin/services/tata-ace-750kg")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.service.label", is("Tata Ace")));
    }

    @Test
    void testToggleStatusAndActiveExclusionInCustomerApp() throws Exception {
        PorterService s1 = new PorterService();
        s1.setServiceId("two-wheeler");
        s1.setName("2 Wheeler");
        s1.setCategory("two_wheeler");
        s1.setBaseFare(49.0);
        s1.setPerKmRate(12.0);
        s1.setIsActive(true);
        s1.setDisplayOrder(1);
        serviceRepository.save(s1);

        PorterService s2 = new PorterService();
        s2.setServiceId("mini-truck");
        s2.setName("Mini Truck");
        s2.setCategory("vehicle");
        s2.setBaseFare(249.0);
        s2.setPerKmRate(22.0);
        s2.setIsActive(true);
        s2.setDisplayOrder(2);
        serviceRepository.save(s2);

        // Verify Customer App sees 2 services
        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.count", is(2)));

        // Admin toggles Mini Truck to INACTIVE
        mockMvc.perform(patch("/api/admin/services/mini-truck/toggle-status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isActive\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.isActive", is(false)));

        // Verify Customer App now only sees 1 active service (2 Wheeler)
        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(1)))
                .andExpect(jsonPath("$.featuredServices[0].serviceId", is("two-wheeler")));

        // Verify GET /api/home also reflects only 1 active service
        mockMvc.perform(get("/api/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featuredServices", hasSize(1)))
                .andExpect(jsonPath("$.featuredServices[0].id", is("two-wheeler")));
    }

    @Test
    void testReorderServices() throws Exception {
        PorterService s1 = new PorterService();
        s1.setServiceId("s1");
        s1.setName("Service 1");
        s1.setIsActive(true);
        s1.setDisplayOrder(1);
        serviceRepository.save(s1);

        PorterService s2 = new PorterService();
        s2.setServiceId("s2");
        s2.setName("Service 2");
        s2.setIsActive(true);
        s2.setDisplayOrder(2);
        serviceRepository.save(s2);

        PorterService s3 = new PorterService();
        s3.setServiceId("s3");
        s3.setName("Service 3");
        s3.setIsActive(true);
        s3.setDisplayOrder(3);
        serviceRepository.save(s3);

        // Reorder: s3 first, then s1, then s2
        String reorderPayload = "{\"serviceIds\": [\"s3\", \"s1\", \"s2\"]}";
        mockMvc.perform(patch("/api/admin/services/reorder")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reorderPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.services[0].serviceId", is("s3")))
                .andExpect(jsonPath("$.services[1].serviceId", is("s1")))
                .andExpect(jsonPath("$.services[2].serviceId", is("s2")));

        // Verify customer app /api/services returns in reordered sequence
        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featuredServices[0].id", is("s3")))
                .andExpect(jsonPath("$.featuredServices[1].id", is("s1")))
                .andExpect(jsonPath("$.featuredServices[2].id", is("s2")));
    }
}

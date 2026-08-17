package com.anushaporter.backend;

import com.anushaporter.backend.model.PorterService;
import com.anushaporter.backend.model.ServiceCategory;
import com.anushaporter.backend.repository.PorterServiceRepository;
import com.anushaporter.backend.repository.ServiceCategoryRepository;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(CustomerServiceIntegrationTest.TestConfig.class)
public class CustomerServiceIntegrationTest {

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
    private ServiceCategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void testGetCustomerServicesGroupedByCategory() throws Exception {
        mockMvc.perform(get("/api/customer/services")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.categories", not(empty())))
                .andExpect(jsonPath("$.categories[0].id").exists())
                .andExpect(jsonPath("$.categories[0].name").exists())
                .andExpect(jsonPath("$.categories[0].slug").exists())
                .andExpect(jsonPath("$.categories[0].services", not(empty())))
                .andExpect(jsonPath("$.categories[0].services[0].id").exists())
                .andExpect(jsonPath("$.categories[0].services[0].categoryId").exists())
                .andExpect(jsonPath("$.categories[0].services[0].categoryName").exists())
                .andExpect(jsonPath("$.categories[0].services[0].basePrice").exists())
                .andExpect(jsonPath("$.categories[0].services[0].perKmRate").exists())
                .andExpect(jsonPath("$.categories[0].services[0].customerAppVisible", is(true)))
                .andExpect(jsonPath("$.categories[0].services[0].isActive", is(true)));
    }

    @Test
    void testGetCategories() throws Exception {
        mockMvc.perform(get("/api/categories")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.categories", not(empty())));
    }

    @Test
    void testGetHomeFeedIncludesEnrichedFields() throws Exception {
        mockMvc.perform(get("/api/home")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.featuredServices", not(empty())))
                .andExpect(jsonPath("$.featuredServices[0].categoryId").exists())
                .andExpect(jsonPath("$.featuredServices[0].categoryName").exists());
    }
}

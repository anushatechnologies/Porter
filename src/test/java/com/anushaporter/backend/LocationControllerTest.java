package com.anushaporter.backend;

import com.anushaporter.backend.service.LocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import org.mockito.Mockito;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(LocationControllerTest.TestConfig.class)
public class LocationControllerTest {

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
    private LocationService locationService;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    public void testLocationAutocomplete() throws Exception {
        mockMvc.perform(get("/api/location/autocomplete").param("input", "Cyber Towers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.predictions").isArray())
                .andExpect(jsonPath("$.predictions[0].placeId").exists())
                .andExpect(jsonPath("$.predictions[0].primaryText").exists())
                .andExpect(jsonPath("$.predictions[0].secondaryText").exists())
                .andExpect(jsonPath("$.predictions[0].fullText").exists());
    }

    @Test
    public void testPlacesAutocompleteAlias() throws Exception {
        mockMvc.perform(get("/api/places/autocomplete").param("input", "DLF Cyber City"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.predictions").isArray())
                .andExpect(jsonPath("$.predictions[0].placeId").exists());
    }

    @Test
    public void testLocationDetails() throws Exception {
        mockMvc.perform(get("/api/location/details").param("placeId", "ChIJbU60yXA_zjsRkW54uoW_aN4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.placeId").value("ChIJbU60yXA_zjsRkW54uoW_aN4"))
                .andExpect(jsonPath("$.data.name").exists())
                .andExpect(jsonPath("$.data.formattedAddress").exists())
                .andExpect(jsonPath("$.data.lat").isNumber())
                .andExpect(jsonPath("$.data.lng").isNumber());
    }

    @Test
    public void testPlacesDetailsAlias() throws Exception {
        mockMvc.perform(get("/api/places/details").param("placeId", "ChIJD7fiBh9NyzsRSc0un6448zo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.placeId").value("ChIJD7fiBh9NyzsRSc0un6448zo"))
                .andExpect(jsonPath("$.data.lat").isNumber())
                .andExpect(jsonPath("$.data.lng").isNumber());
    }
}

package com.anushaporter.backend;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.service.S3ImageService;
import com.anushaporter.backend.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverS3ImageIntegrationTest.TestS3Config.class)
public class DriverS3ImageIntegrationTest {

    @TestConfiguration
    static class TestS3Config {
        @Bean
        @Primary
        public S3Client mockS3Client() {
            S3Client mock = Mockito.mock(S3Client.class);
            doAnswer(invocation -> null).when(mock).putObject(any(PutObjectRequest.class), any(RequestBody.class));
            return mock;
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private S3Client mockS3Client;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        driverRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    void testDriverCreation_MigratesImagesToS3() throws Exception {
        AppUser user = new AppUser();
        user.setName("Ramesh Sharma");
        user.setPhone("9876543210");
        user.setEmail("ramesh@driver.com");
        user.setRole("Admin");
        user.setStatus("Active");
        appUserRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail());
        String base64Sample = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString("dummy binary image".getBytes());

        Driver driver = new Driver();
        driver.setName("Ramesh Sharma");
        driver.setPhone("9876543210");
        driver.setEmail("ramesh@driver.com");
        driver.setVehicleType("Tata Ace");
        driver.setVehicleNumber("KA01AB1234");
        driver.setLicenseUri(base64Sample);
        driver.setRcUri(base64Sample);
        driver.setAadhaarUri(base64Sample);
        driver.setProfilePhotoUri(base64Sample);
        driver.setBankPassbookUri(base64Sample);

        String json = objectMapper.writeValueAsString(driver);

        mockMvc.perform(post("/api/drivers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Ramesh Sharma")))
                .andExpect(jsonPath("$.licenseUri", containsString("s3.ap-south-2.amazonaws.com/license/")))
                .andExpect(jsonPath("$.rcUri", containsString("s3.ap-south-2.amazonaws.com/rc/")))
                .andExpect(jsonPath("$.aadhaarUri", containsString("s3.ap-south-2.amazonaws.com/aadhaar/")))
                .andExpect(jsonPath("$.profilePhotoUri", containsString("s3.ap-south-2.amazonaws.com/profile-photo/")))
                .andExpect(jsonPath("$.bankPassbookUri", containsString("s3.ap-south-2.amazonaws.com/bank-passbook/")));

        Driver savedInDb = driverRepository.findByPhone("9876543210").orElseThrow();
        assertThat(savedInDb.getLicenseUri(), containsString("s3.ap-south-2.amazonaws.com/license/"));
        assertThat(savedInDb.getAadhaarUri(), containsString("s3.ap-south-2.amazonaws.com/aadhaar/"));
        assertThat(savedInDb.getRcUri(), containsString("s3.ap-south-2.amazonaws.com/rc/"));
        assertThat(savedInDb.getProfilePhotoUri(), containsString("s3.ap-south-2.amazonaws.com/profile-photo/"));
        assertThat(savedInDb.getBankPassbookUri(), containsString("s3.ap-south-2.amazonaws.com/bank-passbook/"));
    }

    @Test
    void testDriverRegistration_ProcessesDocumentsToS3() throws Exception {
        AppUser user = new AppUser();
        user.setName("Suresh Raina");
        user.setPhone("9876543212");
        user.setEmail("suresh@driver.com");
        user.setRole("Driver");
        user.setStatus("Active");
        appUserRepository.save(user);

        String token = jwtUtil.generateToken(user.getPhone());
        String base64Sample = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString("dummy binary image".getBytes());

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Suresh Raina");
        payload.put("dob", "1990-01-01");
        payload.put("gender", "Male");
        payload.put("vehicle", "3 Wheeler");
        payload.put("vehicleNumber", "KA02CD5678");
        payload.put("rcNumber", "RC123456789");
        payload.put("aadhaarNumber", "123456789012");
        payload.put("licenseNumber", "DL1234567890");
        payload.put("pincode", "560001");
        payload.put("ifscCode", "HDFC0001234");
        payload.put("accountNumber", "123456789012");

        Map<String, String> documents = new HashMap<>();
        documents.put("profilePhotoUrl", base64Sample);
        documents.put("aadhaarUrl", base64Sample);
        documents.put("licenseUrl", base64Sample);
        documents.put("rcUrl", base64Sample);
        documents.put("bankPassbookUrl", base64Sample);
        payload.put("documents", documents);

        mockMvc.perform(post("/api/drivers/register")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.driver.profilePhotoUri", containsString("s3.ap-south-2.amazonaws.com/profile-photo/")))
                .andExpect(jsonPath("$.driver.aadhaarUri", containsString("s3.ap-south-2.amazonaws.com/aadhaar/")))
                .andExpect(jsonPath("$.driver.licenseUri", containsString("s3.ap-south-2.amazonaws.com/license/")))
                .andExpect(jsonPath("$.driver.rcUri", containsString("s3.ap-south-2.amazonaws.com/rc/")))
                .andExpect(jsonPath("$.driver.bankPassbookUri", containsString("s3.ap-south-2.amazonaws.com/bank-passbook/")));

        Driver registered = driverRepository.findByPhone("9876543212").orElseThrow();
        assertThat(registered.getProfilePhotoUri(), containsString("s3.ap-south-2.amazonaws.com/profile-photo/"));
    }

    @Test
    void testDriverPhotoUpload_UploadsMultipartDirectlyToS3() throws Exception {
        Driver driver = new Driver();
        driver.setName("Ajay Verma");
        driver.setPhone("9876543215");
        driver.setStatus("active");
        driver = driverRepository.save(driver);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "ajay.jpg",
                "image/jpeg",
                "ajay face image bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/driver/photo")
                .file(file)
                .param("driverId", driver.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.url", containsString("s3.ap-south-2.amazonaws.com/profile-photo/")));

        Driver updatedDriver = driverRepository.findById(driver.getId()).orElseThrow();
        assertThat(updatedDriver.getProfilePhotoUri(), containsString("s3.ap-south-2.amazonaws.com/profile-photo/"));
    }
}

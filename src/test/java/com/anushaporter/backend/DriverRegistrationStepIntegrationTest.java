package com.anushaporter.backend;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.DriverRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverRegistrationStepIntegrationTest.TestConfig.class)
public class DriverRegistrationStepIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public S3Client mockS3Client() {
            return Mockito.mock(S3Client.class);
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

    private String testToken;
    private String testPhone = "9876500001";
    private String testEmail = "driver.step.test@anushaporter.com";

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        driverRepository.deleteAll();
        appUserRepository.deleteAll();

        AppUser user = new AppUser();
        user.setEmail(testEmail);
        user.setPhone(testPhone);
        user.setName("Step Test Driver");
        user.setRole("driver");
        appUserRepository.save(user);

        testToken = "Bearer " + jwtUtil.generateToken(testEmail);
    }

    @Test
    public void testDrivingLicence_RejectsSpecialCharacters() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Test Driver");
        payload.put("licenseNumber", "DL-12345/6789"); // contains hyphen and slash

        mockMvc.perform(post("/api/drivers/register")
                        .header("Authorization", testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Driving licence must contain only numbers and alphabets")));
    }

    @Test
    public void testDrivingLicence_RejectsOver100Characters() throws Exception {
        String over100 = "A".repeat(101);
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Test Driver");
        payload.put("licenseNumber", over100);

        mockMvc.perform(post("/api/drivers/register")
                        .header("Authorization", testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Driving licence must contain only numbers and alphabets")));
    }

    @Test
    public void testDrivingLicence_AcceptsAlphanumericUpTo100Characters() throws Exception {
        String validDl = "MH1220230001234";
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Test Driver");
        payload.put("licenseNumber", validDl);
        payload.put("saveAndNext", true);
        payload.put("step", 1);

        mockMvc.perform(post("/api/drivers/register")
                        .header("Authorization", testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.driver.licenseNumber", is(validDl)));

        Driver saved = driverRepository.findByPhone(testPhone).orElseThrow();
        assertEquals(validDl, saved.getLicenseNumber());
    }

    @Test
    public void testMultiStepRegistration_SaveAndNextPersistsToDbAndRetainsDataOnResume() throws Exception {
        // Step 1: Submit Personal Details via Save and Next
        Map<String, Object> step1 = new HashMap<>();
        step1.put("name", "Ramesh Kumar");
        step1.put("dob", "1992-05-10");
        step1.put("gender", "Male");
        step1.put("step", 1);
        step1.put("saveAndNext", true);

        mockMvc.perform(post("/api/drivers/register/save-and-next")
                        .header("Authorization", testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.registrationStep", is(2)))
                .andExpect(jsonPath("$.kycStatus", is("draft")));

        // Verify Step 1 is in DB
        Driver inDbAfterStep1 = driverRepository.findByPhone(testPhone).orElseThrow();
        assertEquals("Ramesh Kumar", inDbAfterStep1.getName());
        assertEquals("1992-05-10", inDbAfterStep1.getDob());
        assertEquals("Male", inDbAfterStep1.getGender());
        assertEquals(2, inDbAfterStep1.getRegistrationStep());
        assertEquals("draft", inDbAfterStep1.getKyc());

        // Driver leaves and returns ("again registration"): Retrieve draft progress
        mockMvc.perform(get("/api/drivers/register/progress")
                        .header("Authorization", testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasDraft", is(true)))
                .andExpect(jsonPath("$.name", is("Ramesh Kumar")))
                .andExpect(jsonPath("$.dob", is("1992-05-10")))
                .andExpect(jsonPath("$.registrationStep", is(2)))
                .andExpect(jsonPath("$.kycStatus", is("draft")));

        // Step 2: Submit Vehicle & Licence Details via Save and Next (does NOT wipe Step 1 name/dob)
        Map<String, Object> step2 = new HashMap<>();
        step2.put("vehicle", "3 Wheeler");
        step2.put("vehicleType", "3 Wheeler");
        step2.put("vehicleNumber", "KA04XY1234");
        step2.put("rcNumber", "RC123456");
        step2.put("licenseNumber", "DL1234567890ABC");
        step2.put("step", 2);
        step2.put("saveAndNext", true);

        mockMvc.perform(post("/api/drivers/register")
                        .header("Authorization", testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.registrationStep", is(3)));

        // Verify DB contains BOTH Step 1 and Step 2 fields
        Driver inDbAfterStep2 = driverRepository.findByPhone(testPhone).orElseThrow();
        assertEquals("Ramesh Kumar", inDbAfterStep2.getName(), "Step 1 name must be preserved");
        assertEquals("1992-05-10", inDbAfterStep2.getDob(), "Step 1 dob must be preserved");
        assertEquals("3 Wheeler", inDbAfterStep2.getVehicle());
        assertEquals("KA04XY1234", inDbAfterStep2.getVehicleNumber());
        assertEquals("DL1234567890ABC", inDbAfterStep2.getLicenseNumber());
        assertEquals(3, inDbAfterStep2.getRegistrationStep());

        // Step 3: Bank & Identity Details
        Map<String, Object> step3 = new HashMap<>();
        step3.put("aadhaarNumber", "123456789012");
        step3.put("pincode", "560001");
        step3.put("bankName", "HDFC Bank");
        step3.put("accountHolderName", "Ramesh Kumar");
        step3.put("accountNumber", "50100012345678");
        step3.put("ifscCode", "HDFC0001234");
        step3.put("step", 3);
        step3.put("saveAndNext", true);

        mockMvc.perform(post("/api/drivers/register/step")
                        .header("Authorization", testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.registrationStep", is(4)));

        // Final Submission: Submit application - auto-approves without requiring admin approval
        Map<String, Object> finalSubmit = new HashMap<>();
        finalSubmit.put("submit", true);

        mockMvc.perform(post("/api/drivers/register")
                        .header("Authorization", testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(finalSubmit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.kycStatus", is("approved")));

        Driver finalInDb = driverRepository.findByPhone(testPhone).orElseThrow();
        assertEquals("approved", finalInDb.getKyc());
        assertEquals("approved", finalInDb.getVerificationStatus());
        assertEquals("Ramesh Kumar", finalInDb.getName());
        assertEquals("DL1234567890ABC", finalInDb.getLicenseNumber());
        assertEquals("50100012345678", finalInDb.getAccountNumber());
    }

    @Test
    public void testAdminDeleteDriver_RemovesProfileFromDatabase() throws Exception {
        // Create a driver in DB
        Driver driver = new Driver();
        driver.setName("Driver To Remove");
        driver.setPhone("9988776655");
        driver.setEmail("remove.me@anushaporter.com");
        driver.setKyc("approved");
        Driver saved = driverRepository.save(driver);
        Long driverId = saved.getId();

        // Admin deletes the driver by numeric ID
        mockMvc.perform(delete("/api/admin/drivers/" + driverId)
                        .header("Authorization", testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("Driver profile removed successfully")));

        // Verify driver is removed from database
        assertTrue(driverRepository.findById(driverId).isEmpty());

        // Deleting non-existent driver returns 404
        mockMvc.perform(delete("/api/admin/drivers/" + driverId)
                        .header("Authorization", testToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));

        // Create another driver and test deletion by DRV- formatted ID
        Driver driver2 = new Driver();
        driver2.setName("Driver Two");
        driver2.setPhone("9988776656");
        Driver saved2 = driverRepository.save(driver2);

        mockMvc.perform(delete("/api/admin/drivers/DRV-" + saved2.getId())
                        .header("Authorization", testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        assertTrue(driverRepository.findById(saved2.getId()).isEmpty());
    }
}

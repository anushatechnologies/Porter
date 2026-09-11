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
                .andExpect(jsonPath("$.isRegistered", is(true)))
                .andExpect(jsonPath("$.registrationCompleted", is(true)))
                .andExpect(jsonPath("$.hasDraft", is(false)))
                .andExpect(jsonPath("$.registrationStep", is(5)))
                .andExpect(jsonPath("$.kycStatus", is("approved")));

        Driver finalInDb = driverRepository.findByPhone(testPhone).orElseThrow();
        assertEquals("approved", finalInDb.getKyc());
        assertEquals("approved", finalInDb.getVerificationStatus());
        assertEquals(5, finalInDb.getRegistrationStep());
        assertEquals("Ramesh Kumar", finalInDb.getName());
        assertEquals("DL1234567890ABC", finalInDb.getLicenseNumber());
        assertEquals("50100012345678", finalInDb.getAccountNumber());

        // Progress check after completion returns hasDraft: false, isRegistered: true, registrationStep: 5
        mockMvc.perform(get("/api/drivers/register/progress")
                        .header("Authorization", testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRegistered", is(true)))
                .andExpect(jsonPath("$.registrationCompleted", is(true)))
                .andExpect(jsonPath("$.hasDraft", is(false)))
                .andExpect(jsonPath("$.registrationStep", is(5)))
                .andExpect(jsonPath("$.kycStatus", is("approved")));

        // Profile check returns isRegistered: true, registrationStep: 5
        mockMvc.perform(get("/api/drivers/me")
                        .header("Authorization", testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRegistered", is(true)))
                .andExpect(jsonPath("$.registrationCompleted", is(true)))
                .andExpect(jsonPath("$.registrationStep", is(5)));
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

    @Test
    public void testPanCard_RejectsInvalidFormat() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Test Driver");
        payload.put("panNumber", "INVALID_PAN_123");

        mockMvc.perform(post("/api/drivers/register")
                        .header("Authorization", testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Invalid PAN card format")));
    }

    @Test
    public void testPanCard_AcceptsValidFormat_PersistsAndReturnsInProfileAndProgress() throws Exception {
        String validPan = "ABCDE1234F";
        String panImageUrl = "https://poteranusha.s3.ap-south-2.amazonaws.com/pan/pan_test.jpg";

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Anusha Driver");
        payload.put("panNumber", validPan);
        payload.put("panUrl", panImageUrl);
        payload.put("submit", true);

        mockMvc.perform(post("/api/drivers/register")
                        .header("Authorization", testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.kycStatus", is("approved")));

        // 1. Verify saved in database
        Driver saved = driverRepository.findByPhone(testPhone).orElseThrow();
        assertEquals(validPan, saved.getPanNumber());
        assertNotNull(saved.getPanUri());

        // 2. Verify GET /api/drivers/me returns panNumber and panUri
        mockMvc.perform(get("/api/drivers/me")
                        .header("Authorization", testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.panNumber", is(validPan)))
                .andExpect(jsonPath("$.panUri", notNullValue()));

        // 3. Verify GET /api/drivers/register/progress returns panNumber and hasDraft: false
        mockMvc.perform(get("/api/drivers/register/progress")
                        .header("Authorization", testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.hasDraft", is(false)))
                .andExpect(jsonPath("$.kycStatus", is("approved")))
                .andExpect(jsonPath("$.panNumber", is(validPan)));
    }

    @Test
    public void testNoDefaultValuesForEmailDobGenderProfilePhoto_RequiresUserEntry() throws Exception {
        // Clear driver repository so fresh registration starts
        driverRepository.deleteAll();

        // 1. Newly created / un-registered driver checking progress
        mockMvc.perform(get("/api/drivers/register/progress")
                        .header("Authorization", testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.dob", is("")))
                .andExpect(jsonPath("$.gender", is("")))
                .andExpect(jsonPath("$.profilePhotoUri", is("")));

        // 2. Newly auto-provisioned profile check via /api/drivers/me
        mockMvc.perform(get("/api/drivers/me")
                        .header("Authorization", testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.dob", is("")))
                .andExpect(jsonPath("$.gender", is("")));

        // 3. Registered person inputs their own real DOB, gender, and email
        Map<String, Object> step1 = new HashMap<>();
        step1.put("name", "Ananya Verma");
        step1.put("dob", "1998-11-20");
        step1.put("gender", "Female");
        step1.put("email", "ananya.verma@example.com");
        step1.put("step", 1);
        step1.put("saveAndNext", true);

        mockMvc.perform(post("/api/drivers/register/save-and-next")
                        .header("Authorization", testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.registrationStep", is(2)));

        // 4. Progress now accurately reflects user-entered data
        mockMvc.perform(get("/api/drivers/register/progress")
                        .header("Authorization", testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Ananya Verma")))
                .andExpect(jsonPath("$.dob", is("1998-11-20")))
                .andExpect(jsonPath("$.gender", is("Female")))
                .andExpect(jsonPath("$.email", is("ananya.verma@example.com")));
    }

    @Test
    public void testOldDriverLoginAndProgress_HealsRegistrationStepTo5AndNeverPromptsAgain() throws Exception {
        // Create an old driver in DB whose registrationStep was left as 2, 3, or null
        Driver oldDriver = new Driver();
        oldDriver.setName("Old Suresh");
        oldDriver.setPhone("9876599999");
        oldDriver.setEmail("old.suresh@anushaporter.com");
        oldDriver.setKyc("approved");
        oldDriver.setVerificationStatus("approved");
        oldDriver.setRegistrationStep(2); // old bug left step as 2
        oldDriver.setVehicle("Auto");
        oldDriver.setVehicleType("Auto");
        driverRepository.save(oldDriver);

        // AppUser corresponding to old driver
        AppUser oldUser = new AppUser();
        oldUser.setName("Old Suresh");
        oldUser.setPhone("9876599999");
        oldUser.setEmail("old.suresh@anushaporter.com");
        oldUser.setRole("Driver");
        appUserRepository.save(oldUser);

        String oldToken = "Bearer " + jwtUtil.generateToken("old.suresh@anushaporter.com");

        // 1. Old driver logs in via POST /api/auth/verify-otp
        Map<String, String> otpBody = new HashMap<>();
        otpBody.put("phone", "9876599999");
        otpBody.put("mode", "login");

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otpBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.isRegistered", is(true)))
                .andExpect(jsonPath("$.registrationCompleted", is(true)))
                .andExpect(jsonPath("$.hasDraft", is(false)))
                .andExpect(jsonPath("$.registrationStep", is(5)))
                .andExpect(jsonPath("$.user.isRegistered", is(true)))
                .andExpect(jsonPath("$.user.registrationStep", is(5)));

        // 2. Old driver checks progress: must return isRegistered: true, registrationStep: 5, hasDraft: false
        mockMvc.perform(get("/api/drivers/register/progress")
                        .header("Authorization", oldToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.isRegistered", is(true)))
                .andExpect(jsonPath("$.registrationCompleted", is(true)))
                .andExpect(jsonPath("$.hasDraft", is(false)))
                .andExpect(jsonPath("$.registrationStep", is(5)))
                .andExpect(jsonPath("$.kycStatus", is("approved")));

        // 3. Old driver checks profile: registrationStep is 5
        mockMvc.perform(get("/api/drivers/me")
                        .header("Authorization", oldToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRegistered", is(true)))
                .andExpect(jsonPath("$.registrationCompleted", is(true)))
                .andExpect(jsonPath("$.registrationStep", is(5)));

        // 4. DB is healed so registrationStep is permanently 5
        Driver healed = driverRepository.findByPhone("9876599999").orElseThrow();
        assertEquals(5, healed.getRegistrationStep());
        assertTrue(healed.isFullyRegistered());
    }
}

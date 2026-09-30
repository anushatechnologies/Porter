package com.anushaporter.backend;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.DriverPayoutAccount;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.repository.DriverPayoutAccountRepository;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.util.IfscBankResolver;
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

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BackendApplication.class)
@Import(DriverBankDetailsAutoResolutionIntegrationTest.TestConfig.class)
public class DriverBankDetailsAutoResolutionIntegrationTest {

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
    private DriverPayoutAccountRepository driverPayoutAccountRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        driverRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    void testIfscBankResolver_DirectMapping() {
        // Test Dhanumjay's exact IFSC code from screenshot
        assertEquals("Union Bank of India", IfscBankResolver.resolveBankName("UBIN6469494"));
        assertEquals("Union Bank of India", IfscBankResolver.resolveBankName("ubin6469494"));

        // Test other major Indian banks
        assertEquals("State Bank of India", IfscBankResolver.resolveBankName("SBIN0001234"));
        assertEquals("HDFC Bank", IfscBankResolver.resolveBankName("HDFC0000123"));
        assertEquals("ICICI Bank", IfscBankResolver.resolveBankName("ICIC0000001"));
        assertEquals("Axis Bank", IfscBankResolver.resolveBankName("UTIB0000001"));
        assertEquals("Punjab National Bank", IfscBankResolver.resolveBankName("PUNB0123400"));
        assertEquals("Bank of Baroda", IfscBankResolver.resolveBankName("BARB0VJMGRE"));
        assertEquals("Canara Bank", IfscBankResolver.resolveBankName("CNRB0001234"));
        assertEquals("Kotak Mahindra Bank", IfscBankResolver.resolveBankName("KKBK0000123"));
        assertEquals("AU Small Finance Bank", IfscBankResolver.resolveBankName("AUBK0001234"));

        // Invalid or null
        assertNull(IfscBankResolver.resolveBankName(null));
        assertNull(IfscBankResolver.resolveBankName(""));
        assertNull(IfscBankResolver.resolveBankName("XYZ"));
        assertNull(IfscBankResolver.resolveBankName("UNKNOWN123"));
    }

    @Test
    void testGetDriverProfile_ResolvesBankNameFromIfscWhenBankNameIsNull() throws Exception {
        String testPhone = "9988776655";

        // Create user and driver mimicking Dhanumjay (bankName is NULL in DB)
        AppUser user = new AppUser();
        user.setName("Dhanumjay");
        user.setPhone(testPhone);
        user.setRole("Driver");
        appUserRepository.save(user);

        Driver driver = new Driver();
        driver.setName("Dhanumjay");
        driver.setPhone(testPhone);
        driver.setAccountHolderName("Dhanumjay");
        driver.setAccountNumber("1941101000254459");
        driver.setIfscCode("UBIN6469494");
        driver.setBankName(null); // Explicitly NULL in DB
        driver.setKyc("approved");
        driver.setStatus("offline");
        driver.setRegistrationStep(5);
        Driver saved = driverRepository.save(driver);

        String token = "Bearer " + jwtUtil.generateToken(testPhone);

        // GET /api/drivers/me MUST automatically return "Union Bank of India"
        mockMvc.perform(get("/api/drivers/me")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountHolderName", is("Dhanumjay")))
                .andExpect(jsonPath("$.accountNumber", is("1941101000254459")))
                .andExpect(jsonPath("$.ifscCode", is("UBIN6469494")))
                .andExpect(jsonPath("$.bankName", is("Union Bank of India")))
                .andExpect(jsonPath("$.bank_name", is("Union Bank of India")));

        // GET /api/drivers/{id} MUST also return "Union Bank of India"
        mockMvc.perform(get("/api/drivers/" + saved.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName", is("Union Bank of India")));
    }

    @Test
    void testRegisterDriver_AutoPopulatesBankNameFromIfscWhenBankNameNotSent() throws Exception {
        String testPhone = "9988776644";
        driverRepository.findByPhone(testPhone).ifPresent(d -> driverRepository.delete(d));
        appUserRepository.findFirstByPhoneOrderByIdDesc(testPhone).ifPresent(u -> appUserRepository.delete(u));

        AppUser user = new AppUser();
        user.setName("Ravi Teja");
        user.setPhone(testPhone);
        user.setRole("Driver");
        appUserRepository.save(user);

        String token = "Bearer " + jwtUtil.generateToken(testPhone);

        // Submit Step 3 without bankName
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Ravi Teja");
        payload.put("accountHolderName", "Ravi Teja");
        payload.put("accountNumber", "112233445566");
        payload.put("ifscCode", "SBIN0001234"); // State Bank of India
        payload.put("saveAndNext", true);

        mockMvc.perform(post("/api/drivers/register")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Verify entity in DB was automatically populated with "State Bank of India"
        Driver inDb = driverRepository.findByPhone(testPhone).orElseThrow();
        assertEquals("State Bank of India", inDb.getBankName());

        // Verify GET /api/drivers/me returns "State Bank of India"
        mockMvc.perform(get("/api/drivers/me")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName", is("State Bank of India")));
    }

    @Test
    void testSavePayoutAccount_ResolvesBankNameAndSyncsWithDriver() throws Exception {
        String testPhone = "9988776633";
        driverRepository.findByPhone(testPhone).ifPresent(d -> driverRepository.delete(d));
        appUserRepository.findFirstByPhoneOrderByIdDesc(testPhone).ifPresent(u -> appUserRepository.delete(u));

        AppUser user = new AppUser();
        user.setName("Mahesh Babu");
        user.setPhone(testPhone);
        user.setRole("Driver");
        appUserRepository.save(user);

        Driver driver = new Driver();
        driver.setName("Mahesh Babu");
        driver.setPhone(testPhone);
        driver.setStatus("offline");
        driver.setKyc("approved");
        driver.setRegistrationStep(5);
        Driver saved = driverRepository.save(driver);

        String token = "Bearer " + jwtUtil.generateToken(testPhone);

        // Save payout account with HDFC IFSC without bankName
        Map<String, String> payload = new HashMap<>();
        payload.put("accountHolderName", "Mahesh Babu");
        payload.put("accountNumber", "50100099887766");
        payload.put("ifscCode", "HDFC0000123");

        mockMvc.perform(post("/api/drivers/me/payout-account")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Verify DriverPayoutAccount has resolved bank name
        DriverPayoutAccount acc = driverPayoutAccountRepository.findByDriverId(saved.getId().toString()).orElseThrow();
        assertEquals("HDFC Bank", acc.getBankName());

        // Verify Driver entity also has updated bankName
        Driver updatedDriver = driverRepository.findById(saved.getId()).orElseThrow();
        assertEquals("HDFC Bank", updatedDriver.getBankName());

        // Verify profile endpoint returns "HDFC Bank"
        mockMvc.perform(get("/api/drivers/me")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName", is("HDFC Bank")));
    }
}

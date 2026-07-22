package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.service.EmailService;
import com.anushaporter.backend.util.JwtUtil;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.model.Driver;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@RestController

public class AuthController {

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtUtil jwtUtil;

    // Login endpoint
    @PostMapping("/api/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        
        // Support 'username', 'email', or 'phone' fields
        String identifier = body.get("username");
        if (identifier == null) identifier = body.get("phone");
        if (identifier == null) identifier = body.get("email");
        
        String password = body.get("password");

        if (identifier == null || password == null) {
            response.put("success", false);
            response.put("message", "Phone/Email and password are required");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<AppUser> userOpt;
        if (identifier.contains("@")) {
            userOpt = userRepository.findByEmail(identifier);
        } else {
            userOpt = userRepository.findByPhone(identifier);
            if (userOpt.isEmpty()) {
                userOpt = userRepository.findByEmail(identifier); // fallback
            }
        }

        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            if (user.getPassword() != null && BCrypt.checkpw(password, user.getPassword())) {
                if ("Pending Approval".equals(user.getStatus())) {
                    response.put("success", false);
                    response.put("message", "Account is pending admin approval.");
                    return ResponseEntity.ok(response);
                }

                Map<String, String> userProfile = new HashMap<>();
                userProfile.put("name", user.getName());
                userProfile.put("role", user.getRole());
                userProfile.put("email", user.getEmail());
                userProfile.put("phone", user.getPhone());
                userProfile.put("avatar", "https://api.dicebear.com/7.x/initials/svg?seed=" + (user.getName() != null ? user.getName() : "User"));

                String token = jwtUtil.generateToken(user.getEmail());

                response.put("success", true);
                response.put("user", userProfile);
                response.put("token", token);
                return ResponseEntity.ok(response);
            }
        }

        response.put("success", false);
        response.put("message", "Invalid credentials");
        return ResponseEntity.ok(response);
    }

    // Logout endpoint
    @PostMapping("/api/auth/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    // Signup endpoint - registers a new user (no OTP required)
    @PostMapping("/api/auth/signup")
    public ResponseEntity<Map<String, Object>> signup(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        String email = body.get("email");

        if (userRepository.findByEmail(email).isPresent()) {
            response.put("success", false);
            response.put("message", "Email already registered.");
            return ResponseEntity.ok(response);
        }

        AppUser newUser = new AppUser();
        newUser.setName(body.get("fullName"));
        newUser.setEmail(email);
        newUser.setPhone(body.get("phone"));
        newUser.setCompany(body.get("company"));
        newUser.setRole(body.getOrDefault("role", "Support Agent"));
        newUser.setStatus("Active");

        // Hash password
        String hashedPassword = BCrypt.hashpw(body.get("password"), BCrypt.gensalt());
        newUser.setPassword(hashedPassword);

        userRepository.save(newUser);

        System.out.println("New user registered: " + email);

        response.put("success", true);
        response.put("message", "Account created successfully. You can now login.");
        return ResponseEntity.ok(response);
    }

    // Send OTP endpoint (for phone login/signup)
    @PostMapping("/api/auth/send-otp")
    public ResponseEntity<Map<String, Object>> sendOtp(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        String phone = body.getOrDefault("phone", body.get("mobile"));
        String mode = body.getOrDefault("mode", "login"); // default to login for drivers
        String name = body.get("name"); // optional for signup

        if (phone == null || phone.isEmpty()) {
            response.put("success", false);
            response.put("message", "Phone number is required.");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<AppUser> userOpt = userRepository.findByPhone(phone);
        AppUser user;

        if ("login".equalsIgnoreCase(mode)) {
            if (!userOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "User not found. Please create an account.");
                return ResponseEntity.ok(response);
            }
            user = userOpt.get();
        } else if ("signup".equalsIgnoreCase(mode)) {
            if (userOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "User already exists. Please login instead.");
                return ResponseEntity.ok(response);
            }
            user = new AppUser();
            user.setPhone(phone);
            user.setName(name);
            user.setEmail(phone + "@porterapp.com"); // Dummy email for compatibility
            
            String requestedRole = body.getOrDefault("role", "customer");
            user.setRole(requestedRole);
            user.setStatus("Active");
        } else {
            response.put("success", false);
            response.put("message", "Invalid mode.");
            return ResponseEntity.badRequest().body(response);
        }

        String otp = String.format("%04d", new Random().nextInt(10000));
        user.setOtp(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        System.out.println("=================================================");
        System.out.println("OTP for phone " + phone + " is: " + otp);
        System.out.println("=================================================");

        response.put("success", true);
        response.put("message", "OTP sent successfully.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/auth/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        String email = body.get("email");
        String phone = body.getOrDefault("phone", body.get("mobile"));
        String otp = body.get("otp");

        Optional<AppUser> userOpt;
        if (phone != null && !phone.isEmpty()) {
            userOpt = userRepository.findByPhone(phone);
        } else {
            userOpt = userRepository.findByEmail(email);
        }

        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            if (user.getOtp() != null && user.getOtp().equals(otp)) {
                if (LocalDateTime.now().isBefore(user.getOtpExpiry())) {
                    user.setStatus("Active");
                    user.setOtp(null);
                    user.setOtpExpiry(null);
                    userRepository.save(user);

                    Map<String, Object> userProfile = new HashMap<>();
                    userProfile.put("name", user.getName());
                    userProfile.put("role", user.getRole());
                    userProfile.put("email", user.getEmail());
                    userProfile.put("phone", user.getPhone());
                    userProfile.put("avatar", "https://api.dicebear.com/7.x/initials/svg?seed=" + (user.getName() != null ? user.getName() : "User"));
                    
                    // Driver Specific ID and KYC
                    Optional<Driver> driverOpt = driverRepository.findByPhone(user.getPhone());
                    if (driverOpt.isPresent()) {
                        userProfile.put("id", driverOpt.get().getId().toString());
                        userProfile.put("kycStatus", driverOpt.get().getKyc() != null ? driverOpt.get().getKyc() : "pending");
                    } else {
                        userProfile.put("id", user.getId().toString());
                        userProfile.put("kycStatus", "unregistered");
                    }

                    String token = jwtUtil.generateToken(user.getEmail());

                    response.put("success", true);
                    response.put("message", "Account activated successfully.");
                    response.put("user", userProfile);
                    response.put("token", token);
                    return ResponseEntity.ok(response);
                } else {
                    response.put("success", false);
                    response.put("message", "OTP has expired.");
                    return ResponseEntity.ok(response);
                }
            }
        }

        response.put("success", false);
        response.put("message", "Invalid OTP.");
        return ResponseEntity.ok(response);
    }

    // Resend OTP endpoint
    @PostMapping("/api/auth/resend-otp")
    public ResponseEntity<Map<String, Object>> resendOtp(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        String phone = body.get("phone");
        String email = body.get("email");

        Optional<AppUser> userOpt;
        if (phone != null && !phone.isEmpty()) {
            userOpt = userRepository.findByPhone(phone);
        } else {
            userOpt = userRepository.findByEmail(email);
        }

        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            String otp = String.format("%04d", new Random().nextInt(10000));
            user.setOtp(otp);
            user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
            userRepository.save(user);

            System.out.println("=================================================");
            System.out.println("Resent OTP for " + (phone != null ? phone : email) + " is: " + otp);
            System.out.println("=================================================");

            response.put("success", true);
            response.put("message", "OTP resent successfully.");
            return ResponseEntity.ok(response);
        }

        response.put("success", false);
        response.put("message", "User not found.");
        return ResponseEntity.ok(response);
    }
    // Forgot Password endpoint
    @PostMapping("/api/auth/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        String email = body.get("email");

        Optional<AppUser> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            String otp = String.format("%04d", new Random().nextInt(10000));
            user.setOtp(otp);
            user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
            userRepository.save(user);

            // Log OTP to console
            System.out.println("=================================================");
            System.out.println("Password Reset OTP for email " + email + " is: " + otp);
            System.out.println("=================================================");

            try {
                emailService.sendPasswordResetEmail(email, otp);
            } catch (Exception e) {
                System.err.println("Failed to send reset email: " + e.getMessage());
            }
        }

        // Always return success to prevent email enumeration
        response.put("success", true);
        response.put("message", "If an account with that email exists, a reset code has been sent.");
        return ResponseEntity.ok(response);
    }

    // Reset Password endpoint
    @PostMapping("/api/auth/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        String email = body.get("email");
        String otp = body.get("otp");
        String newPassword = body.get("newPassword");

        Optional<AppUser> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            if (user.getOtp() != null && user.getOtp().equals(otp)) {
                if (LocalDateTime.now().isBefore(user.getOtpExpiry())) {
                    user.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
                    user.setOtp(null);
                    user.setOtpExpiry(null);
                    userRepository.save(user);

                    response.put("success", true);
                    response.put("message", "Password reset successfully. You can now log in.");
                    return ResponseEntity.ok(response);
                } else {
                    response.put("success", false);
                    response.put("message", "OTP has expired.");
                    return ResponseEntity.ok(response);
                }
            }
        }

        response.put("success", false);
        response.put("message", "Invalid OTP.");
        return ResponseEntity.ok(response);
    }

    // --- AppUser CRUD (keep under /api/users to match frontend) ---
    @GetMapping("/api/users")
    public List<AppUser> getAllUsers() {
        return userRepository.findAll();
    }

    @PostMapping("/api/users")
    public ResponseEntity<Map<String, Object>> saveUsers(@RequestBody List<AppUser> users) {
        userRepository.saveAll(users);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    // Update Profile endpoint
    @PutMapping("/api/users/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                response.put("success", false);
                response.put("message", "Unauthorized");
                return ResponseEntity.status(401).body(response);
            }
            
            String token = authHeader.substring(7);
            String email = jwtUtil.getUsernameFromToken(token);
            
            Optional<AppUser> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                AppUser user = userOpt.get();
                
                if (body.containsKey("name")) user.setName(body.get("name"));
                if (body.containsKey("phone")) user.setPhone(body.get("phone"));
                
                userRepository.save(user);
                
                response.put("success", true);
                response.put("message", "Profile updated successfully.");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "User not found.");
                return ResponseEntity.status(404).body(response);
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Invalid token or server error.");
            return ResponseEntity.status(401).body(response);
        }
    }
}

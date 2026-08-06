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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import java.util.Random;

@RestController

public class AuthController {

    @Autowired
    private AppUserRepository userRepository;

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
            userOpt = userRepository.findFirstByEmailOrderByIdDesc(identifier);
        } else {
            userOpt = userRepository.findFirstByPhoneOrderByIdDesc(identifier);
            if (userOpt.isEmpty()) {
                userOpt = userRepository.findFirstByEmailOrderByIdDesc(identifier); // fallback
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

                Map<String, Object> userProfile = new HashMap<>();
                userProfile.put("id", user.getId());
                userProfile.put("name", user.getName());
                userProfile.put("role", user.getRole());
                userProfile.put("email", user.getEmail());
                userProfile.put("phone", user.getPhone());
                userProfile.put("avatar", "https://api.dicebear.com/7.x/initials/svg?seed=" + (user.getName() != null ? user.getName().replace(" ", "") : "User"));

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

        if (email != null && !email.trim().isEmpty()
                && userRepository.findFirstByEmailOrderByIdDesc(email.trim()).isPresent()) {
            response.put("success", false);
            response.put("message", "Email already registered.");
            return ResponseEntity.ok(response);
        }

        AppUser newUser = new AppUser();
        newUser.setName(body.get("name") != null ? body.get("name") : body.get("fullName"));
        newUser.setEmail(email);
        newUser.setPhone(body.get("phone"));
        newUser.setCompany(body.get("company"));
        newUser.setRole(body.getOrDefault("role", "Support Agent"));
        newUser.setStatus("Active");

        // Hash password
        String hashedPassword = BCrypt.hashpw(body.get("password"), BCrypt.gensalt());
        newUser.setPassword(hashedPassword);

        AppUser savedUser = userRepository.save(newUser);

        System.out.println("New user registered: " + email);
        
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("id", savedUser.getId());
        userProfile.put("name", savedUser.getName());
        userProfile.put("email", savedUser.getEmail());
        userProfile.put("role", savedUser.getRole());

        response.put("success", true);
        response.put("message", "Account created successfully. You can now login.");
        response.put("user", userProfile);
        return ResponseEntity.ok(response);
    }

    // Helper method
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 10) return phone;
        return "+91******" + phone.substring(phone.length() - 4);
    }

    // Send OTP endpoint (for phone login/signup)
    @PostMapping("/api/auth/send-otp")
    public ResponseEntity<Map<String, Object>> sendOtp(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "This endpoint is deprecated. Please use Firebase SDK on the client to send OTPs.");
        return ResponseEntity.status(410).body(response);
    }

    @PostMapping("/api/auth/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        String firebaseIdToken = body.get("firebaseIdToken");
        String mode = body.get("mode"); // "login" or "signup"
        String name = body.get("name"); // only for signup

        if (firebaseIdToken == null || firebaseIdToken.isEmpty()) {
            response.put("success", false);
            response.put("message", "firebaseIdToken is required");
            return ResponseEntity.badRequest().body(response);
        }

        // Clean the token (remove accidentally injected quotes or whitespaces by frontend HTTP clients)
        firebaseIdToken = firebaseIdToken.replaceAll("\"", "")
                                         .replaceAll("\\r\\n|\\r|\\n", "")
                                         .replaceAll("\\s+", "")
                                         .trim();

        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseIdToken);
            String phone = (String) decodedToken.getClaims().get("phone_number");
            
            if (phone == null || phone.isEmpty()) {
                response.put("success", false);
                response.put("message", "No phone number attached to this Firebase credential.");
                return ResponseEntity.status(400).body(response);
            }

            String localPhone = phone.replaceAll("\\D+", "");
            if (localPhone.length() > 10) {
                localPhone = localPhone.substring(localPhone.length() - 10);
            }

            Optional<AppUser> userOpt = userRepository.findFirstByPhoneOrderByIdDesc(localPhone);
            AppUser user;

            if ("signup".equalsIgnoreCase(mode)) {
                if (userOpt.isPresent()) {
                    response.put("success", false);
                    response.put("message", "Account already exists. Please login.");
                    return ResponseEntity.status(409).body(response);
                }
                user = new AppUser();
                user.setPhone(localPhone);
                user.setName(name != null ? name : "User");
                user.setEmail(localPhone + "@porterapp.com"); // Dummy email
                user.setRole("customer");
                user.setStatus("Active");
                userRepository.save(user);
            } else {
                // login
                if (!userOpt.isPresent()) {
                    response.put("success", false);
                    response.put("message", "No account found with this number.");
                    return ResponseEntity.status(404).body(response);
                }
                user = userOpt.get();
                if ("Pending".equals(user.getStatus())) {
                    user.setStatus("Active");
                    userRepository.save(user);
                }
            }

            String accessToken = jwtUtil.generateToken(user.getEmail());
            String refreshToken = jwtUtil.generateToken(user.getEmail());

            Map<String, Object> userProfile = new HashMap<>();
            userProfile.put("id", user.getId().toString());
            userProfile.put("name", user.getName());
            userProfile.put("phone", user.getPhone());
            userProfile.put("email", user.getEmail() != null && user.getEmail().contains("@porterapp.com") ? null : user.getEmail());
            userProfile.put("isPhoneVerified", true);

            response.put("success", true);
            response.put("accessToken", accessToken);
            response.put("refreshToken", refreshToken);
            response.put("user", userProfile);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Invalid Firebase ID Token: " + e.getMessage());
            return ResponseEntity.status(401).body(response);
        }
    }

    @PostMapping("/api/auth/resend-otp")
    public ResponseEntity<Map<String, Object>> resendOtp(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "This endpoint is deprecated. Please use Firebase SDK on the client to resend OTPs.");
        return ResponseEntity.status(410).body(response);
    }
    // Forgot Password endpoint
    @PostMapping("/api/auth/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        String email = body.get("email");

        Optional<AppUser> userOpt = (email == null || email.trim().isEmpty())
                ? Optional.empty()
                : userRepository.findFirstByEmailOrderByIdDesc(email.trim());
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

        Optional<AppUser> userOpt = (email == null || email.trim().isEmpty())
                ? Optional.empty()
                : userRepository.findFirstByEmailOrderByIdDesc(email.trim());

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
            
            Optional<AppUser> userOpt = (email == null || email.trim().isEmpty())
                    ? Optional.empty()
                    : userRepository.findFirstByEmailOrderByIdDesc(email.trim());
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

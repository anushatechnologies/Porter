package com.anushaporter.backend.config;

import com.anushaporter.backend.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
            return true;
        }

        String path = request.getRequestURI();
        // Allow tracking, delivery OTP, order/booking creation, and cancellation endpoints without hard blocking on token expiration
        if (path.endsWith("/delivery-otp") || path.endsWith("/otp") || path.endsWith("/tracking") || path.endsWith("/cancel") ||
                ("POST".equalsIgnoreCase(request.getMethod()) &&
                        (path.equals("/api/orders") || path.equals("/api/orders/") || path.startsWith("/api/bookings")))) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            String subject = jwtUtil.extractIdentifierFromFirebaseOrJwt(token);
            if (subject != null && !subject.isBlank()) {
                request.setAttribute("userId", subject);
                return true;
            }
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\": false, \"error\": \"Unauthorized\", \"message\": \"Your session has expired. Please login again.\"}");
        return false;
    }
}

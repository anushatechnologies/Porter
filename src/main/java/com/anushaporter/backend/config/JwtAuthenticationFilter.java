package com.anushaporter.backend.config;

import com.anushaporter.backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip filtering for auth endpoints, static resources, and admin panel endpoints
        return path.startsWith("/api/auth/") 
            || path.startsWith("/h2-console") 
            || path.startsWith("/api/location/")
            || path.startsWith("/api/places/")
            || path.startsWith("/api/cities")
            || path.startsWith("/api/admin/")
            || path.startsWith("/api/orders")
            || path.startsWith("/api/drivers")
            || path.startsWith("/api/driver")
            || path.startsWith("/api/payouts")
            || path.startsWith("/api/tickets")
            || path.startsWith("/api/notifications")
            || path.startsWith("/api/customers")
            || path.startsWith("/api/vehicles")
            || path.startsWith("/api/services")
            || path.startsWith("/api/home")
            || path.startsWith("/api/ratings")
            || path.startsWith("/api/content")
            || path.startsWith("/api/bookings")
            || path.startsWith("/api/franchises")
            || path.startsWith("/api/settings")
            || path.startsWith("/api/users")
            || path.startsWith("/api/pricing")
            || path.startsWith("/api/payments")
            || path.startsWith("/api/admin")
            || path.startsWith("/api/upload")
            || path.equals("/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Also permit preflight CORS requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Missing or invalid Authorization header\"}");
            return;
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Invalid or expired JWT token\"}");
            return;
        }

        // Token is valid, proceed with the request
        filterChain.doFilter(request, response);
    }
}

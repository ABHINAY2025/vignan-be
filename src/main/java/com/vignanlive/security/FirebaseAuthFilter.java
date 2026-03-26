package com.vignanlive.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.vignanlive.service.AuthService;
import com.vignanlive.service.RoleCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirebaseAuthFilter extends OncePerRequestFilter {

    private final FirebaseAuth firebaseAuth;
    private final AuthService authService;
    private final RoleCacheService roleCacheService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String idToken = authHeader.substring(7);

        try {
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
            String email = decodedToken.getEmail();
            if (email == null) {
                filterChain.doFilter(request, response);
                return;
            }
            email = email.toLowerCase();

            // Check cache first
            RoleCacheService.CachedRole cached = roleCacheService.get(email);
            String role;
            Map<String, Object> profile;

            if (cached != null) {
                role = cached.role();
                profile = cached.profile();
            } else {
                AuthService.RoleResult result = authService.detectRole(email);
                role = result.role();
                profile = result.profile();

                if (role != null) {
                    roleCacheService.put(email, role, profile);
                }
            }

            FirebaseUserDetails userDetails = new FirebaseUserDetails(
                    email, role != null ? role : "unregistered", profile);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            log.error("Firebase token verification failed: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or expired token\",\"status\":401}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}

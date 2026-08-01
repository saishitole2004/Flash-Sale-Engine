package com.flashsale.flash_sale_engine.filter;
import com.flashsale.flash_sale_engine.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    // Constructor Injection
    public JwtFilter(UserDetailsService userDetailsService, JwtUtil jwtUtil) {
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 1. Extract Authorization header from incoming HTTP request
        String authorizationHeader = request.getHeader("Authorization");
        String username = null;
        String jwt = null;

        // 2. Check if header exists and starts with "Bearer "
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7); // Remove "Bearer " prefix
            username = jwtUtil.extractUsername(jwt);
        }

        // 3. If username exists and user isn't authenticated in this context yet
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Fetch user details from MySQL via UserDetailsService
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // Validate token signature & expiry
            if (jwtUtil.validateToken(jwt)) {
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                System.out.println("LOGGED IN USER: " + userDetails.getUsername() + " | AUTHORITIES: " + userDetails.getAuthorities());
                // Attach metadata (IP address, session details)
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Authenticate the user for this request thread
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // 4. Pass request down the remaining filter chain
        chain.doFilter(request, response);
    }
}
package org.ilynosov.hw_2.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        long start = System.currentTimeMillis();

        String requestId = UUID.randomUUID().toString();
        response.setHeader("X-Request-Id", requestId);

        HttpServletRequest requestToUse = request;

        if ("POST".equals(request.getMethod())
                || "PUT".equals(request.getMethod())
                || "DELETE".equals(request.getMethod())) {

            requestToUse = new CachedBodyHttpServletRequest(request);
        }

        try {

            filterChain.doFilter(requestToUse, response);

        } finally {

            long duration = System.currentTimeMillis() - start;

            Map<String, Object> log = new HashMap<>();
            log.put("request_id", requestId);
            log.put("method", request.getMethod());
            log.put("endpoint", request.getRequestURI());
            log.put("status_code", response.getStatus());
            log.put("duration_ms", duration);
            log.put("timestamp", Instant.now().toString());

            String userId = request.getHeader("X-User-Id");
            log.put("user_id", userId);

            if (requestToUse instanceof CachedBodyHttpServletRequest cached) {
                log.put("body", cached.getBody());
            }

            System.out.println(objectMapper.writeValueAsString(log));
        }
    }
}
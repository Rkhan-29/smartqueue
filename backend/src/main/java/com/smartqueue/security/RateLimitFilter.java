package com.smartqueue.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.config.AppProperties;
import com.smartqueue.exception.ApiError;
import com.smartqueue.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/** Per-IP throttling for credential endpoints and anonymous token creation. */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final Pattern JOIN_PATH = Pattern.compile("^/api/queues/[^/]+/join/?$");

    private final RateLimiter limiter;
    private final AppProperties props;
    private final ObjectMapper mapper;

    public RateLimitFilter(RateLimiter limiter, AppProperties props, ObjectMapper mapper) {
        this.limiter = limiter;
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String bucket = null;
        int limit = 0;
        if ("POST".equals(request.getMethod())) {
            String path = request.getRequestURI();
            if (path.equals("/api/auth/login") || path.equals("/api/auth/register")
                    || path.equals("/api/auth/refresh")) {
                bucket = "auth";
                limit = props.security().rateLimitPerMinute();
            } else if (JOIN_PATH.matcher(path).matches()) {
                bucket = "join";
                limit = props.security().joinRateLimitPerMinute();
            }
        }
        if (bucket != null && !limiter.tryAcquire(bucket + ":" + request.getRemoteAddr(), limit, WINDOW_MILLIS)) {
            response.setStatus(ErrorCode.RATE_LIMITED.status().value());
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), ApiError.of(ErrorCode.RATE_LIMITED, request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }
}

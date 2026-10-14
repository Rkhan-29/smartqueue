package com.smartqueue.service;

import com.smartqueue.config.AppProperties;
import com.smartqueue.dto.AuthDtos.AuthResponse;
import com.smartqueue.dto.AuthDtos.LoginRequest;
import com.smartqueue.dto.AuthDtos.RegisterRequest;
import com.smartqueue.dto.AuthDtos.UserDto;
import com.smartqueue.entity.AppUser;
import com.smartqueue.entity.RefreshToken;
import com.smartqueue.entity.Role;
import com.smartqueue.entity.UserStatus;
import com.smartqueue.exception.ApiException;
import com.smartqueue.exception.ErrorCode;
import com.smartqueue.repository.AppUserRepository;
import com.smartqueue.repository.RefreshTokenRepository;
import com.smartqueue.security.JwtService;
import com.smartqueue.security.TokenHasher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AppProperties props;
    private final AuditService audit;
    /** Compared against when the login is unknown, so response time does not reveal valid accounts. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       JwtService jwt, AppProperties props, AuditService audit) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
        this.audit = audit;
        this.dummyHash = encoder.encode(TokenHasher.randomUrlSafe(16));
    }

    /** noRollbackFor: failed-attempt counters and audit rows must persist when login is rejected. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest req) {
        Instant now = Instant.now();
        String login = req.login().trim();
        AppUser user = users.findByLogin(login).orElse(null);

        if (user == null) {
            encoder.matches(req.password(), dummyHash);
            audit.recordAnonymous(login, "LOGIN_FAILED", "USER", null, Map.of("reason", "unknown_login"));
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.isLocked(now)) {
            audit.recordAs(user, "LOGIN_BLOCKED", "USER", user.getId(), Map.of("reason", "locked"));
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            if (attempts >= props.security().loginMaxAttempts()) {
                user.setLockedUntil(now.plus(Duration.ofMinutes(props.security().loginLockoutMinutes())));
                user.setFailedAttempts(0);
            } else {
                user.setFailedAttempts(attempts);
            }
            users.save(user);
            audit.recordAs(user, "LOGIN_FAILED", "USER", user.getId(), Map.of("reason", "bad_password"));
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            audit.recordAs(user, "LOGIN_BLOCKED", "USER", user.getId(), Map.of("reason", "disabled"));
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }

        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        users.save(user);
        audit.recordAs(user, "LOGIN", "USER", user.getId(), null);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.emailExists(email)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "An account with this email already exists.");
        }
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setUsername("c-" + TokenHasher.randomUrlSafe(9));
        user.setFullName(req.fullName().trim());
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRole(Role.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        users.save(user);
        audit.recordAs(user, "CUSTOMER_REGISTERED", "USER", user.getId(), null);
        return issueTokens(user);
    }

    /** Rotates the refresh token. Presenting an already-used token revokes every session of that user. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));
        AppUser user = stored.getUser();

        if (stored.isRevoked()) {
            refreshTokens.revokeAllForUser(user.getId());
            audit.recordAs(user, "REFRESH_TOKEN_REUSE", "USER", user.getId(), null);
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (stored.getExpiresAt().isBefore(Instant.now()) || user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        stored.setRevoked(true);
        refreshTokens.save(stored);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(TokenHasher.sha256Hex(rawToken)).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokens.save(t);
            audit.recordAs(t.getUser(), "LOGOUT", "USER", t.getUser().getId(), null);
        });
    }

    @Transactional(readOnly = true)
    public UserDto me(UUID userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        return UserDto.from(user);
    }

    @Transactional
    public void revokeAllSessions(UUID userId) {
        refreshTokens.revokeAllForUser(userId);
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        refreshTokens.deleteExpired(Instant.now().minus(Duration.ofDays(1)));
    }

    private AuthResponse issueTokens(AppUser user) {
        String raw = TokenHasher.randomUrlSafe(48);
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(TokenHasher.sha256Hex(raw));
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(props.jwt().refreshTokenTtlDays())));
        refreshTokens.save(token);
        return new AuthResponse(jwt.createAccessToken(user), raw, jwt.accessTokenTtl().toSeconds(),
                UserDto.from(user));
    }
}

package com.smartqueue.dto;

import com.smartqueue.entity.AppUser;
import com.smartqueue.entity.Role;
import com.smartqueue.entity.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    /** At least 10 chars with lower, upper, digit and symbol; max 72 (BCrypt input limit). */
    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^\\w\\s]).{10,72}$";
    public static final String PASSWORD_MESSAGE =
            "Password must be 10-72 characters and include upper and lower case letters, a number and a symbol.";

    public record LoginRequest(
            @NotBlank @Size(max = 190) String login,
            @NotBlank @Size(max = 72) String password) {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password) {
    }

    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {
    }

    public record UserDto(UUID id, String email, String username, String fullName, Role role,
                          UserStatus status, UUID organizationId) {
        public static UserDto from(AppUser u) {
            return new UserDto(u.getId(), u.getEmail(), u.getUsername(), u.getFullName(), u.getRole(),
                    u.getStatus(), u.getOrganization() == null ? null : u.getOrganization().getId());
        }
    }

    public record AuthResponse(String accessToken, String refreshToken, long expiresInSeconds, UserDto user) {
    }
}

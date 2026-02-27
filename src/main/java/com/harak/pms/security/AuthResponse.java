package com.harak.pms.security;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String username,
        String role
) {
}


package com.harak.pms.user;

import com.harak.pms.audit.AuditService;
import com.harak.pms.security.AuthResponse;
import com.harak.pms.security.RefreshTokenRequest;
import com.harak.pms.security.jwt.JwtProvider;
import com.harak.pms.security.jwt.RefreshToken;
import com.harak.pms.security.jwt.RefreshTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtProvider jwtProvider;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody UserRegistrationRequest request) {
        User user = userService.registerUser(request.username(), request.password(), request.role());
        auditService.logEvent("USER_REGISTERED", "USER", user.getId(), user.getUsername());
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody UserLoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        User user = userService.findByUsername(request.username());
        String accessToken = jwtProvider.generateToken(user.getUsername(), user.getRole());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        return ResponseEntity.ok(new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                user.getUsername(),
                user.getRole()
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshToken existingToken = refreshTokenService.verifyRefreshToken(request.refreshToken());
        User user = userService.findUserById(existingToken.getUserId());

        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(
                request.refreshToken(), user.getId());
        String accessToken = jwtProvider.generateToken(user.getUsername(), user.getRole());

        auditService.logEvent("TOKEN_REFRESH", "USER", user.getId(), user.getUsername());

        return ResponseEntity.ok(new AuthResponse(
                accessToken,
                newRefreshToken.getToken(),
                user.getUsername(),
                user.getRole()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshToken token = refreshTokenService.verifyRefreshToken(request.refreshToken());
        refreshTokenService.revokeAllUserTokens(token.getUserId());

        User user = userService.findUserById(token.getUserId());
        auditService.logEvent("LOGOUT", "USER", user.getId(), user.getUsername());

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PutMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.changePassword(username, request.currentPassword(), request.newPassword());

        refreshTokenService.revokeAllUserTokens(user.getId());
        auditService.logEvent("PASSWORD_CHANGED", "USER", user.getId(), username);

        return ResponseEntity.ok(Map.of("message", "Password changed successfully. Please login again."));
    }
}

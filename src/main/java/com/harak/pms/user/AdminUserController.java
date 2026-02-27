package com.harak.pms.user;

import com.harak.pms.audit.AuditService;
import com.harak.pms.security.jwt.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, String>> disableUser(@PathVariable UUID id) {
        User user = userService.disableUser(id);
        refreshTokenService.revokeAllUserTokens(id);

        String adminUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        auditService.logEvent("USER_DISABLED", "USER", id, adminUsername,
                null, "Disabled user: " + user.getUsername());

        return ResponseEntity.ok(Map.of("message", "User account disabled: " + user.getUsername()));
    }
}


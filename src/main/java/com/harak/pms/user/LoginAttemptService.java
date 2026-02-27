package com.harak.pms.user;

import com.harak.pms.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public void loginSucceeded(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (user.getFailedLoginAttempts() > 0) {
                user.setFailedLoginAttempts(0);
                user.setLockExpiresAt(null);
                userRepository.save(user);
            }
        });
    }

    @Transactional
    public void loginFailed(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);

            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockExpiresAt(Instant.now().plus(LOCK_DURATION_MINUTES, ChronoUnit.MINUTES));
                log.warn("Account locked for user {} after {} failed attempts", username, attempts);
                auditService.logEvent("ACCOUNT_LOCKED", "USER", user.getId(), username);
            }

            userRepository.save(user);
        });
    }

    @Transactional(readOnly = true)
    public boolean isAccountLocked(String username) {
        return userRepository.findByUsername(username)
                .map(User::isAccountLocked)
                .orElse(false);
    }
}


package com.harak.pms.audit;

import com.harak.pms.user.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationAuditListener {

    private final AuditService auditService;
    private final LoginAttemptService loginAttemptService;

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        String ip = extractIpAddress(event.getAuthentication().getDetails());

        loginAttemptService.loginSucceeded(username);
        auditService.logEvent("LOGIN_SUCCESS", "USER", null, username, ip, null);
    }

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication().getName();
        String ip = extractIpAddress(event.getAuthentication().getDetails());

        loginAttemptService.loginFailed(username);
        auditService.logEvent("LOGIN_FAILURE", "USER", null, username, ip, null);
    }

    private String extractIpAddress(Object details) {
        if (details instanceof WebAuthenticationDetails webDetails) {
            return webDetails.getRemoteAddress();
        }
        return "unknown";
    }
}

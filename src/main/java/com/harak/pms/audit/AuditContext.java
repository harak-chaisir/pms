package com.harak.pms.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Utility class providing common audit context values.
 *
 * <p>Centralizes the extraction of the authenticated username and client IP address,
 * eliminating duplicate private helper methods across controllers. Used by
 * {@link AuditAspect} and available for direct use in non-AOP audit call sites
 * (e.g., security filters and event listeners).
 *
 * <p>This class is not instantiable — all methods are static.
 */
public final class AuditContext {

    private AuditContext() {
        // Utility class — prevent instantiation
    }

    /**
     * Retrieves the username of the currently authenticated principal.
     *
     * @return the authenticated username, or {@code "anonymous"} if no authentication is present.
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "anonymous";
    }

    /**
     * Extracts the client IP address from the given HTTP request.
     *
     * <p>Checks the {@code X-Forwarded-For} header first to support reverse-proxied
     * deployments. If the header contains multiple addresses (comma-separated), the
     * first (leftmost) address — representing the original client — is returned.
     *
     * @param request the HTTP request to extract the IP from.
     * @return the client IP address, or the remote address if no forwarding header is present.
     */
    public static String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Retrieves the current {@link HttpServletRequest} from Spring's {@link RequestContextHolder}.
     *
     * <p>This is used by {@link AuditAspect} when the controller method does not
     * explicitly accept an {@code HttpServletRequest} parameter.
     *
     * @return an {@link Optional} containing the current request, or empty if not in a web context.
     */
    public static Optional<HttpServletRequest> getCurrentHttpRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest);
    }
}


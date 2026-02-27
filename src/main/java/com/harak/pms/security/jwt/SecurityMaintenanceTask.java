package com.harak.pms.security.jwt;

import com.harak.pms.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduled tasks for security maintenance:
 * - Purge expired/revoked refresh tokens (daily at 2 AM)
 * - Evict stale rate-limit buckets (every 10 minutes)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityMaintenanceTask {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RateLimitFilter rateLimitFilter;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        int deleted = refreshTokenRepository.deleteExpiredOrRevoked(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired/revoked refresh tokens", deleted);
        }
    }

    @Scheduled(fixedRate = 600_000) // every 10 minutes
    public void evictRateLimitBuckets() {
        rateLimitFilter.evictStaleBuckets();
    }
}


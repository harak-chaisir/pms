package com.harak.pms.encryption;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryptor for PHI fields.
 * Ciphertext format: Base64(IV[12] + ciphertext + authTag[16])
 */
@Slf4j
@Component
public class StringEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    @Value("${phi.encryption-key:}")
    private String encodedKey;

    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException(
                    "PHI_ENCRYPTION_KEY is not set. HIPAA requires encryption of PHI at rest. "
                            + "Generate a key with: openssl rand -base64 32");
        }
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "PHI_ENCRYPTION_KEY must be exactly 32 bytes (256 bits). Current: "
                            + keyBytes.length + " bytes. Generate with: openssl rand -base64 32");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("PHI encryption initialized successfully");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            byte[] combined = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt PHI data", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            // Encrypted data must be at least IV (12 bytes) + GCM auth tag (16 bytes)
            if (combined.length < IV_LENGTH + (TAG_LENGTH_BITS / 8)) {
                log.warn("Data too short to be AES-GCM ciphertext — treating as legacy plaintext. "
                        + "Run the PHI migration task to encrypt existing records.");
                return ciphertext;
            }

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);

            return new String(plaintext);
        } catch (IllegalArgumentException e) {
            // Base64 decoding failed — data is legacy unencrypted plaintext
            log.warn("Found unencrypted PHI data in database — returning as-is. "
                    + "Run the PHI migration task to encrypt existing records.");
            return ciphertext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt PHI data", e);
        }
    }
}


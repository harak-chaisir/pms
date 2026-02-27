package com.harak.pms.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter that transparently encrypts/decrypts PHI string fields
 * using AES-256-GCM. Apply to entity fields with @Convert(converter = PhiEncryptionConverter.class).
 */
@Component
@Converter
@RequiredArgsConstructor
public class PhiEncryptionConverter implements AttributeConverter<String, String> {

    private final StringEncryptor stringEncryptor;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return stringEncryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return stringEncryptor.decrypt(dbData);
    }
}


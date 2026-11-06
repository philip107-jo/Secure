package edu.secure.assign.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AesCrypto {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesCrypto(@Value("${assign.aes-key:}") String base64Key) {

        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("AES 암호화 키가 설정되지 않았습니다.");
        }

        try {
            byte[] decodedKey = Base64.getDecoder().decode(base64Key);

            if (decodedKey.length != 16) {
                throw new IllegalArgumentException("AES 키는 16바이트여야 합니다.");
            }

            this.secretKey = new SecretKeySpec(decodedKey, "AES");

        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("AES 키 설정이 올바르지 않습니다.", e);
        }
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] result = new byte[iv.length + encrypted.length];

            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(result);

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    public String decrypt(String token) {
        try {
            byte[] decoded = Base64.getDecoder().decode(token);

            if (decoded.length <= IV_LENGTH) {
                throw new IllegalArgumentException("잘못된 암호문입니다.");
            }

            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[decoded.length - IV_LENGTH];

            System.arraycopy(decoded, 0, iv, 0, IV_LENGTH);
            System.arraycopy(decoded, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plain = cipher.doFinal(encrypted);

            return new String(plain, StandardCharsets.UTF_8);

        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("복호화 실패", e);
        }
    }
}
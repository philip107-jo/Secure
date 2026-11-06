package edu.secure.assign.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 비밀번호 저장/검증 */
public final class PasswordHasher {

    private static final BCryptPasswordEncoder ENCODER =
            new BCryptPasswordEncoder();

    private PasswordHasher() {}

    public static String hash(String password) {
        return ENCODER.encode(password);
    }

    public static boolean verify(String password, String stored) {
        return ENCODER.matches(password, stored);
    }
}
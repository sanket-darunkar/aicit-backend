package com.aicit.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Stateless JWT utility.
 *
 * Admin token claims:   sub=email, role=MCA_ADMIN|SUPER_ADMIN
 * Institute token claims: sub=email, role=INSTITUTE_ADMIN|INSTITUTE_STAFF,
 *                         instituteId=<id>, instituteName=<name>
 */
@Component
@Slf4j
public class JwtUtil {

    private final SecretKey signingKey;
    private final long      expirationMs;

    public JwtUtil(
            @Value("${app.jwt.secret}")         String secret,
            @Value("${app.jwt.expiration-ms}")  long   expirationMs) {

        byte[] keyBytes = decodeSecret(secret);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                "AICIT_JWT_SECRET is too short (" + keyBytes.length + " bytes). " +
                "Minimum 32 bytes (256 bits) required. " +
                "Run: python3 -c \"import secrets; print(secrets.token_hex(32))\"");
        }

        this.signingKey   = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    /**
     * Accepts three secret formats:
     *  1. 64-char lowercase hex (e.g. from secrets.token_hex(32)) — preferred, no special chars
     *  2. Base64-encoded string
     *  3. Raw UTF-8 string (fallback, usually too short)
     */
    private static byte[] decodeSecret(String secret) {
        // Hex: exactly 64 lowercase hex chars = 32 bytes
        if (secret.matches("[0-9a-fA-F]{64,}")) {
            byte[] result = new byte[secret.length() / 2];
            for (int i = 0; i < result.length; i++) {
                result[i] = (byte) Integer.parseInt(secret.substring(i * 2, i * 2 + 2), 16);
            }
            return result;
        }
        // Base64
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded.length >= 32) return decoded;
        } catch (IllegalArgumentException ignored) {}
        // Raw UTF-8 fallback
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    // ── Token generation ─────────────────────────────────────

    public String generateAdminToken(String email, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        return build(claims, email);
    }

    public String generateInstituteToken(String email, String role,
                                         Long instituteId, String instituteName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role",          role);
        claims.put("instituteId",   instituteId);
        claims.put("instituteName", instituteName);
        return build(claims, email);
    }

    private String build(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    // ── Validation & extraction ───────────────────────────────

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public Long extractInstituteId(String token) {
        Object val = parseClaims(token).get("instituteId");
        if (val == null) return null;
        if (val instanceof Long l) return l;
        if (val instanceof Integer i) return i.longValue();
        return Long.parseLong(val.toString());
    }

    public String extractInstituteName(String token) {
        return parseClaims(token).get("instituteName", String.class);
    }

    // ── Internal ──────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

package com.example.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationInMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(UserPrincipal userPrincipal) {
        log.debug("[JWT_GENERATE_TOKEN]: DEBUG - Generating JWT token for user: '{}' ({})",
                userPrincipal.getUsername(), userPrincipal.getRoleName());

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userPrincipal.getUserId());
        claims.put("fullName", userPrincipal.getFullName());
        claims.put("roleId", userPrincipal.getRoleId());
        claims.put("roleName", userPrincipal.getRoleName());
        claims.put("unitCabang", userPrincipal.getUnitCabang());

        String token = Jwts.builder()
                .claims(claims)
                .subject(userPrincipal.getUsername())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();

        log.info("[JWT_GENERATE_TOKEN]: SUCCESS - JWT token generated for user '{}', expires at: {}",
                userPrincipal.getUsername(), expiryDate);
        return token;
    }

    public String getUsernameFromJwt(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    public String getUserIdFromJwt(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("userId", String.class);
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (SecurityException | MalformedJwtException ex) {
            log.warn("[JWT_VALIDATE_TOKEN]: WARN - Invalid JWT signature: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.warn("[JWT_VALIDATE_TOKEN]: WARN - Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.warn("[JWT_VALIDATE_TOKEN]: WARN - Unsupported JWT token format: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("[JWT_VALIDATE_TOKEN]: WARN - Empty JWT claims: {}", ex.getMessage());
        }
        return false;
    }
}

package com.apply.diarypic.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Component
@Getter
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secretString;

    @Value("${jwt.expiration-ms}")
    private long accessTokenExpiresIn;

    @Value("${jwt.refresh-secret}")
    private String refreshSecretString;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshTokenExpiresIn;

    private SecretKey secretKey;
    private SecretKey refreshSecretKey;

    @PostConstruct
    protected void init() {
        byte[] keyBytes = Base64.getDecoder().decode(secretString);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);

        byte[] refreshKeyBytes = Base64.getDecoder().decode(refreshSecretString);
        this.refreshSecretKey = Keys.hmacShaKeyFor(refreshKeyBytes);
    }

    public String generateAccessToken(Long userId) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + accessTokenExpiresIn);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + refreshTokenExpiresIn);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(refreshSecretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        return validateTokenInternal(token, secretKey);
    }

    public boolean validateRefreshToken(String token) {
        return validateTokenInternal(token, refreshSecretKey);
    }

    private boolean validateTokenInternal(String token, SecretKey key) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }

    public Claims getClaims(String token) {
        return getClaimsInternal(token, secretKey);
    }

    public Claims getClaimsFromRefreshToken(String token) {
        return getClaimsInternal(token, refreshSecretKey);
    }

    private Claims getClaimsInternal(String token, SecretKey key) {
        try {
            return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(getClaims(token).getSubject());
    }

    public Long getUserIdFromRefreshToken(String token) {
        return Long.valueOf(getClaimsFromRefreshToken(token).getSubject());
    }

    public String getProvider(String token) {
        Claims claims = getClaims(token);
        return claims.get("provider", String.class);
    }
}
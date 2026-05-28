package com.codesense.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class JwtService {

  @Value("${app.jwt.secret}")
  private String jwtSecret;

  @Value("${app.jwt.expiration}")
  private long jwtExpiration;

  private Key getSigningKey() {
    byte[] keyBytes = jwtSecret.getBytes();
    return Keys.hmacShaKeyFor(keyBytes);
  }

  public String generateToken(String userId, String email) {
    return Jwts.builder()
        .subject(userId)
        .claim("email", email)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
        .signWith(getSigningKey())
        .compact();
  }

  public String extractUserId(String token) {
    return extractClaims(token).getSubject();
  }

  public String extractEmail(String token) {
    return extractClaims(token).get("email", String.class);
  }

  public boolean isTokenValid(String token) {
    try {
      Claims claims = extractClaims(token);
      return claims.getExpiration().after(new Date());
    } catch (Exception e) {
      log.error("JWT validation error: {}", e.getMessage());
      return false;
    }
  }

  private Claims extractClaims(String token) {
    return Jwts.parser()
        .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}

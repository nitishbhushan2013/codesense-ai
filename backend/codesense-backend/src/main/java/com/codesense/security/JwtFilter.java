package com.codesense.security;

import com.codesense.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final UserRepository userRepository;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String token = extractTokenFromCookie(request);

    log.debug(
        "JWT filter: {} {} | cookies={} | token={}",
        request.getMethod(),
        request.getRequestURI(),
        request.getCookies() != null
            ? Arrays.stream(request.getCookies())
                .map(Cookie::getName)
                .collect(java.util.stream.Collectors.joining(","))
            : "NONE",
        token != null ? token.substring(0, Math.min(10, token.length())) + "..." : "NULL");

    if (token != null && jwtService.isTokenValid(token)) {
      String userId = jwtService.extractUserId(token);
      String email = jwtService.extractEmail(token);

      UserDetails userDetails =
          User.builder().username(userId).password("").authorities(new ArrayList<>()).build();

      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

      SecurityContextHolder.getContext().setAuthentication(authentication);
      log.debug("Authenticated user: {}", email);
    }

    filterChain.doFilter(request, response);
  }

  private String extractTokenFromCookie(HttpServletRequest request) {
    if (request.getCookies() == null) return null;
    return Arrays.stream(request.getCookies())
        .filter(cookie -> "jwt".equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }
}

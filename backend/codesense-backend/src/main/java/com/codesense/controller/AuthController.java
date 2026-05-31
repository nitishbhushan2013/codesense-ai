package com.codesense.controller;

import com.codesense.dto.AuthResponse;
import com.codesense.dto.LoginRequest;
import com.codesense.dto.RegisterRequest;
import com.codesense.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    try {
      AuthResponse response = authService.register(request);
      return ResponseEntity.ok(response);
    } catch (RuntimeException e) {
      return ResponseEntity.badRequest()
          .body(AuthResponse.builder().message(e.getMessage()).build());
    }
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse response) {
    try {
      String token = authService.login(request);
      setJwtCookie(response, token, servletRequest.isSecure());
      return ResponseEntity.ok(AuthResponse.builder().message("Login successful").build());
    } catch (RuntimeException e) {
      return ResponseEntity.badRequest()
          .body(AuthResponse.builder().message(e.getMessage()).build());
    }
  }

  @GetMapping("/me")
  public ResponseEntity<AuthResponse> getCurrentUser(
      @AuthenticationPrincipal UserDetails userDetails) {
    try {
      AuthResponse response = authService.getCurrentUser(userDetails.getUsername());
      return ResponseEntity.ok(response);
    } catch (RuntimeException e) {
      return ResponseEntity.badRequest()
          .body(AuthResponse.builder().message(e.getMessage()).build());
    }
  }

  @PostMapping("/logout")
  public ResponseEntity<AuthResponse> logout(
      HttpServletRequest servletRequest, HttpServletResponse response) {
    ResponseCookie cookie =
        ResponseCookie.from("jwt", "")
            .httpOnly(true)
            .path("/")
            .maxAge(0)
            .sameSite(servletRequest.isSecure() ? "None" : "Lax")
            .secure(servletRequest.isSecure())
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    return ResponseEntity.ok(AuthResponse.builder().message("Logged out successfully").build());
  }

  private void setJwtCookie(HttpServletResponse response, String token, boolean secure) {
    ResponseCookie cookie =
        ResponseCookie.from("jwt", token)
            .httpOnly(true)
            .path("/")
            .maxAge(86400)
            .sameSite(secure ? "None" : "Lax")
            .secure(secure)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}

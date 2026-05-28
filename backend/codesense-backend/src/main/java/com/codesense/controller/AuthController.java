package com.codesense.controller;

import com.codesense.dto.AuthResponse;
import com.codesense.dto.LoginRequest;
import com.codesense.dto.RegisterRequest;
import com.codesense.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
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
      @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
    try {
      String token = authService.login(request);

      Cookie cookie = new Cookie("jwt", token);
      cookie.setHttpOnly(true);
      cookie.setPath("/");
      cookie.setMaxAge(86400);
      response.addCookie(cookie);

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
  public ResponseEntity<AuthResponse> logout(HttpServletResponse response) {
    Cookie cookie = new Cookie("jwt", null);
    cookie.setHttpOnly(true);
    cookie.setPath("/");
    cookie.setMaxAge(0);
    response.addCookie(cookie);

    return ResponseEntity.ok(AuthResponse.builder().message("Logged out successfully").build());
  }
}

package com.codesense.service;

import com.codesense.dto.AuthResponse;
import com.codesense.dto.LoginRequest;
import com.codesense.dto.RegisterRequest;
import com.codesense.model.User;
import com.codesense.repository.UserRepository;
import com.codesense.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new RuntimeException("Email already registered");
    }

    User user =
        User.builder()
            .name(request.getName())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .build();

    User savedUser = userRepository.save(user);

    log.info("New user registered: {}", savedUser.getEmail());

    return AuthResponse.builder()
        .id(savedUser.getId().toString())
        .name(savedUser.getName())
        .email(savedUser.getEmail())
        .message("Registration successful")
        .build();
  }

  public String login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("Invalid email or password"));

    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      throw new RuntimeException("Invalid email or password");
    }

    log.info("User logged in: {}", user.getEmail());

    return jwtService.generateToken(user.getId().toString(), user.getEmail());
  }

  public AuthResponse getCurrentUser(String userId) {
    User user =
        userRepository
            .findById(java.util.UUID.fromString(userId))
            .orElseThrow(() -> new RuntimeException("User not found"));

    return AuthResponse.builder()
        .id(user.getId().toString())
        .name(user.getName())
        .email(user.getEmail())
        .avatarUrl(user.getAvatarUrl())
        .build();
  }
}

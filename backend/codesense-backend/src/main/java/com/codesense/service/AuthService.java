package com.codesense.service;

import com.codesense.dto.AuthResponse;
import com.codesense.dto.RegisterRequest;
import com.codesense.model.User;
import com.codesense.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

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

    return AuthResponse.builder()
        .id(savedUser.getId().toString())
        .name(savedUser.getName())
        .email(savedUser.getEmail())
        .message("Registration successful")
        .build();
  }
}

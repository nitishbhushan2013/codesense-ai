package com.codesense.security;

import com.codesense.model.User;
import com.codesense.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

  private final UserRepository userRepository;
  private final JwtService jwtService;

  @Value("${FRONTEND_URL:http://localhost:3000}")
  private String frontendUrl;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {

    OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

    Integer githubIdInt = oauth2User.getAttribute("id");
    String githubId = String.valueOf(githubIdInt);
    String name = oauth2User.getAttribute("name");
    String email = oauth2User.getAttribute("email");
    String avatarUrl = oauth2User.getAttribute("avatar_url");

    if (name == null) {
      name = oauth2User.getAttribute("login");
    }

    Optional<User> existingUser = userRepository.findByGithubId(githubId);

    User user;
    if (existingUser.isPresent()) {
      user = existingUser.get();
      log.info("Existing GitHub user logged in: {}", user.getEmail());
    } else {
      user = User.builder().githubId(githubId).name(name).email(email).avatarUrl(avatarUrl).build();
      user = userRepository.save(user);
      log.info("New GitHub user created: {}", user.getEmail());
    }

    String token =
        jwtService.generateToken(
            user.getId().toString(), user.getEmail() != null ? user.getEmail() : githubId);

    ResponseCookie cookie =
        ResponseCookie.from("jwt", token)
            .httpOnly(true)
            .path("/")
            .maxAge(86400)
            .sameSite(request.isSecure() ? "None" : "Lax")
            .secure(request.isSecure())
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

    response.sendRedirect(frontendUrl + "/dashboard");
  }
}

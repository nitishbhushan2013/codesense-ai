package com.codesense.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codesense.model.ChatMessage;
import com.codesense.model.Review;
import com.codesense.model.User;
import com.codesense.repository.ChatMessageRepository;
import com.codesense.repository.ReviewRepository;
import com.codesense.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class ChatControllerIntegrationTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository userRepository;
  @Autowired private ReviewRepository reviewRepository;
  @Autowired private ChatMessageRepository chatMessageRepository;

  private MockMvc mockMvc;
  private User owner;
  private Review review;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    chatMessageRepository.deleteAll();
    reviewRepository.deleteAll();
    userRepository.deleteAll();

    owner =
        userRepository.save(
            User.builder().name("Owner").email("owner@example.com").password("x").build());

    review =
        reviewRepository.save(
            Review.builder()
                .user(owner)
                .submissionType("paste")
                .language("Java")
                .blobKey("blob-1")
                .score(80)
                .summary("ok")
                .status("completed")
                .build());

    chatMessageRepository.save(
        ChatMessage.builder().review(review).role("user").content("What is wrong?").build());
    chatMessageRepository.save(
        ChatMessage.builder().review(review).role("assistant").content("Line 3 leaks.").build());
  }

  @Test
  void ownerGetsChatHistoryOldestFirst() throws Exception {
    mockMvc
        .perform(get("/api/reviews/{id}/chat", review.getId()).with(user(owner.getId().toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].role").value("user"))
        .andExpect(jsonPath("$[0].content").value("What is wrong?"))
        .andExpect(jsonPath("$[1].role").value("assistant"));
  }

  @Test
  void anonymousRequestIsRejected() throws Exception {
    // No principal: SecurityConfig's anyRequest().authenticated() blocks it before the
    // controller runs (Spring Security default entry point -> 401/403, never 2xx).
    mockMvc
        .perform(get("/api/reviews/{id}/chat", review.getId()))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void nonOwnerIsForbidden() throws Exception {
    User other =
        userRepository.save(
            User.builder().name("Other").email("other@example.com").password("x").build());

    mockMvc
        .perform(get("/api/reviews/{id}/chat", review.getId()).with(user(other.getId().toString())))
        .andExpect(status().isForbidden());
  }

  @Test
  void unknownReviewIsNotFound() throws Exception {
    mockMvc
        .perform(
            get("/api/reviews/{id}/chat", UUID.randomUUID()).with(user(owner.getId().toString())))
        .andExpect(status().isNotFound());
  }
}

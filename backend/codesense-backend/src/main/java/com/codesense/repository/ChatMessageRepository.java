package com.codesense.repository;

import com.codesense.model.ChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

  List<ChatMessage> findByReviewIdOrderByCreatedAtAsc(UUID reviewId);
}

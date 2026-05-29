package com.codesense.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "findings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Finding {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(columnDefinition = "uuid", updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "review_id", nullable = false)
  private Review review;

  @Column(nullable = false, length = 20)
  private String category;

  @Column(nullable = false, length = 20)
  private String severity;

  @Column(name = "line_reference", length = 100)
  private String lineReference;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "suggested_fix", columnDefinition = "TEXT")
  private String suggestedFix;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }
}

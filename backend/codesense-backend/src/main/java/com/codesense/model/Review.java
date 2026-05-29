package com.codesense.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(columnDefinition = "uuid", updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "submission_type", nullable = false)
  private String submissionType;

  private String language;

  @Column(name = "pr_url", length = 500)
  private String prUrl;

  @Column(name = "blob_key", length = 500)
  private String blobKey;

  private Integer score;

  @Column(columnDefinition = "TEXT")
  private String summary;

  @Column(nullable = false)
  @Builder.Default
  private String status = "completed";

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<Finding> findings = new ArrayList<>();

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }

  public void addFinding(Finding finding) {
    findings.add(finding);
    finding.setReview(this);
  }
}

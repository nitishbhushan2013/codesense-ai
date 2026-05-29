export interface User {
  id: string;
  name: string;
  email: string;
  avatarUrl?: string;
}

// Mirrors backend FindingCategory / FindingDto.category values.
export type FindingCategory = "bug" | "security" | "performance" | "quality";

// Mirrors backend FindingDto.severity values.
export type FindingSeverity = "critical" | "warning" | "info";

// Mirrors backend dto/FindingDto (a Java record). The backend does not send an
// `id` on findings (FindingDto has no id field), so it is optional here.
export interface Finding {
  id?: string;
  category: FindingCategory;
  severity: FindingSeverity;
  lineReference: string;
  description: string;
  suggestedFix: string;
}

// Mirrors backend dto/ReviewResponse (a Java record). `id` is null for
// anonymous, ephemeral reviews that are never persisted; `language` and `prUrl`
// are null depending on submissionType.
export interface Review {
  id: string | null;
  submissionType: "pr_url" | "paste";
  language?: string | null;
  prUrl?: string | null;
  score: number;
  summary: string;
  status: string;
  findings: Finding[];
  createdAt: string;
}

export interface ChatMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  createdAt: string;
}

export interface SubmissionRequest {
  submissionType: "pr_url" | "paste";
  prUrl?: string;
  code?: string;
  language?: string;
}

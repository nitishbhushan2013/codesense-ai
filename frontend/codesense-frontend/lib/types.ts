export type FindingCategory = "bug" | "security" | "performance" | "quality";
export type FindingSeverity = "critical" | "warning" | "info";

export interface User {
  id: string;
  name: string;
  email: string;
  avatarUrl?: string;
}

export interface Finding {
  id: string;
  category: FindingCategory;
  severity: FindingSeverity;
  lineReference: string;
  description: string;
  suggestedFix: string;
}

export interface Review {
  id: string;
  submissionType: "pr_url" | "paste";
  language?: string;
  prUrl?: string;
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

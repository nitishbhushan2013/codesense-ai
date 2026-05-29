"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/app/auth-context";
import { useReview } from "@/app/review-context";
import ReviewSummary from "@/components/ReviewSummary";
import ReviewCard from "@/components/ReviewCard";
import type { FindingCategory, FindingSeverity } from "@/lib/types";

type CategoryTab = "all" | FindingCategory;
type SeverityFilter = "all" | FindingSeverity;

const CATEGORY_TABS: { key: CategoryTab; label: string }[] = [
  { key: "all", label: "All" },
  { key: "bug", label: "Bugs" },
  { key: "security", label: "Security" },
  { key: "performance", label: "Performance" },
  { key: "quality", label: "Quality" },
];

const SEVERITY_OPTIONS: { key: SeverityFilter; label: string }[] = [
  { key: "all", label: "All severities" },
  { key: "critical", label: "Critical" },
  { key: "warning", label: "Warning" },
  { key: "info", label: "Info" },
];

export default function ReviewPage() {
  // The review is read from context (set by SubmitForm on a successful POST).
  // We intentionally do NOT refetch by id here:
  //   - Anonymous reviews are ephemeral (id: null, never persisted) — there is
  //     nothing to fetch.
  //   - Persisted reviews have an id, but GET /api/reviews/{id} does not exist
  //     yet (STORY-301 backend scope), so a direct load / refresh can't recover
  //     the data either.
  // TODO(STORY-301): once GET /api/reviews/{id} exists, fall back to fetching
  // by `useParams().id` when the context is empty (the persisted-review,
  // direct-load / refresh path). Leave the anonymous case as context-only.
  const { review } = useReview();
  const { user } = useAuth();

  const [category, setCategory] = useState<CategoryTab>("all");
  const [severity, setSeverity] = useState<SeverityFilter>("all");

  const filtered = useMemo(() => {
    if (!review) return [];
    return review.findings.filter((f) => {
      const categoryOk = category === "all" || f.category === category;
      const severityOk = severity === "all" || f.severity === severity;
      return categoryOk && severityOk;
    });
  }, [review, category, severity]);

  // No review in context: this is a direct load / refresh. Surface the gap
  // honestly rather than papering over it with a fake fetch.
  if (!review) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-20 text-center">
        <div className="text-6xl mb-4">🔍</div>
        <h1 className="text-2xl font-bold text-white mb-2">
          Review not available
        </h1>
        <p className="text-gray-400 mb-6">
          This review result is only available right after you submit it.
          Anonymous reviews are not saved, and re-opening a saved review by link
          is not supported yet.
        </p>
        <Link
          href="/"
          className="inline-block bg-blue-600 hover:bg-blue-700 text-white px-6 py-3 rounded-lg font-medium transition"
        >
          Run a new review
        </Link>
      </div>
    );
  }

  // Anonymous when there is no logged-in user (and the review id is null).
  const isAnonymous = !user || review.id === null;

  return (
    <div className="max-w-4xl mx-auto px-4 py-10">
      <ReviewSummary review={review} showSignUpNudge={isAnonymous} />

      {/* Category tabs */}
      <div className="flex flex-wrap gap-2 mb-4">
        {CATEGORY_TABS.map((tab) => {
          const count =
            tab.key === "all"
              ? review.findings.length
              : review.findings.filter((f) => f.category === tab.key).length;
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => setCategory(tab.key)}
              data-testid={`category-tab-${tab.key}`}
              className={`text-sm px-3 py-1.5 rounded-lg border transition ${
                category === tab.key
                  ? "bg-blue-600 border-blue-600 text-white"
                  : "bg-gray-800 border-gray-700 text-gray-400 hover:text-white"
              }`}
            >
              {tab.label} ({count})
            </button>
          );
        })}
      </div>

      {/* Severity filter */}
      <div className="flex items-center gap-2 mb-6">
        <label htmlFor="severity-filter" className="text-sm text-gray-500">
          Severity
        </label>
        <select
          id="severity-filter"
          value={severity}
          onChange={(e) => setSeverity(e.target.value as SeverityFilter)}
          className="bg-gray-800 border border-gray-700 text-white text-sm px-3 py-1.5 rounded-lg focus:outline-none focus:border-blue-500"
        >
          {SEVERITY_OPTIONS.map((opt) => (
            <option key={opt.key} value={opt.key}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>

      {/* Findings */}
      {filtered.length === 0 ? (
        <div
          data-testid="no-findings"
          className="bg-gray-900 border border-gray-700 rounded-xl p-10 text-center text-gray-400"
        >
          {review.findings.length === 0
            ? "No issues found. Nice work!"
            : "No findings match the current filters."}
        </div>
      ) : (
        <div className="space-y-4" data-testid="findings-list">
          {filtered.map((finding, i) => (
            <ReviewCard
              key={finding.id ?? `${finding.category}-${i}`}
              finding={finding}
              language={review.language}
            />
          ))}
        </div>
      )}
    </div>
  );
}

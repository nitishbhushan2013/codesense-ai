"use client";

import type { Review } from "@/lib/types";

function scoreColor(score: number): string {
  if (score >= 80) return "text-green-400";
  if (score >= 60) return "text-yellow-400";
  return "text-red-400";
}

function scoreLabel(score: number): string {
  if (score >= 80) return "Good";
  if (score >= 60) return "Fair";
  return "Needs Work";
}

export default function ReviewSummary({ review }: { review: Review }) {
  return (
    <div className="bg-gray-900 border border-gray-700 rounded-2xl p-6 mb-8">
      <div className="flex items-start justify-between gap-6 flex-wrap">
        <div className="flex items-end gap-3">
          <span className={`text-6xl font-bold tabular-nums ${scoreColor(review.score)}`}>
            {review.score}
          </span>
          <div className="mb-1">
            <div className="text-gray-500 text-sm leading-none mb-1">/100</div>
            <span
              className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${
                review.score >= 80
                  ? "bg-green-900/30 text-green-400 border-green-700"
                  : review.score >= 60
                    ? "bg-yellow-900/30 text-yellow-400 border-yellow-700"
                    : "bg-red-900/30 text-red-400 border-red-700"
              }`}
            >
              {scoreLabel(review.score)}
            </span>
          </div>
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-2">
            <span className="text-xs text-gray-400 bg-gray-800 border border-gray-700 px-2.5 py-0.5 rounded-full">
              {review.submissionType === "pr_url"
                ? "PR Review"
                : (review.language ?? "Code Paste")}
            </span>
            {review.createdAt && (
              <span className="text-xs text-gray-600">
                {new Date(review.createdAt).toLocaleDateString(undefined, {
                  month: "short",
                  day: "numeric",
                  year: "numeric",
                })}
              </span>
            )}
          </div>
          <p className="text-gray-300 leading-relaxed text-sm">{review.summary}</p>
        </div>
      </div>
    </div>
  );
}

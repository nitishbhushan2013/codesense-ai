"use client";

import Link from "next/link";
import type { Review } from "@/lib/types";

function scoreColor(score: number): string {
  if (score >= 80) return "text-green-400";
  if (score >= 50) return "text-yellow-400";
  return "text-red-400";
}

export default function ReviewSummary({
  review,
  showSignUpNudge,
}: {
  review: Review;
  showSignUpNudge: boolean;
}) {
  return (
    <div className="bg-gray-900 border border-gray-700 rounded-2xl p-6 md:p-8 mb-8">
      <div className="flex items-start justify-between gap-6 flex-wrap">
        <div className="flex-1 min-w-[16rem]">
          <h1 className="text-2xl font-bold text-white mb-2">Review results</h1>
          <p className="text-gray-300 whitespace-pre-wrap">{review.summary}</p>

          <div className="flex items-center gap-3 mt-4 text-xs text-gray-500 flex-wrap">
            <span className="px-2 py-0.5 rounded bg-gray-800 border border-gray-700">
              {review.submissionType === "pr_url" ? "GitHub PR" : "Pasted code"}
            </span>
            {review.language && (
              <span className="px-2 py-0.5 rounded bg-gray-800 border border-gray-700">
                {review.language}
              </span>
            )}
            {review.prUrl && (
              <a
                href={review.prUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="text-blue-400 hover:text-blue-300 underline truncate max-w-xs"
              >
                {review.prUrl}
              </a>
            )}
            <span>
              {review.findings.length}{" "}
              {review.findings.length === 1 ? "finding" : "findings"}
            </span>
          </div>
        </div>

        {/* Score dial */}
        <div className="text-center">
          <div
            data-testid="review-score"
            className={`text-5xl font-bold ${scoreColor(review.score)}`}
          >
            {review.score}
          </div>
          <div className="text-xs text-gray-500 mt-1">/ 100</div>
        </div>
      </div>

      {showSignUpNudge && (
        <div
          data-testid="signup-nudge"
          className="mt-6 bg-blue-900/30 border border-blue-700 rounded-xl px-4 py-3 flex items-center justify-between gap-4 flex-wrap"
        >
          <p className="text-sm text-blue-200">
            This review is not saved. Sign up to keep your review history and
            re-open past reviews.
          </p>
          <Link
            href="/auth/register"
            className="bg-blue-600 hover:bg-blue-700 text-white text-sm px-4 py-2 rounded-lg transition whitespace-nowrap"
          >
            Sign up free
          </Link>
        </div>
      )}
    </div>
  );
}

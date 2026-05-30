"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/app/auth-context";
import { useReview } from "@/app/review-context";
import ChatPanel from "@/components/ChatPanel";
import api from "@/lib/api";
import type { Review } from "@/lib/types";

const SEVERITY_COLORS: Record<string, string> = {
  critical: "bg-red-900/30 text-red-400 border-red-700",
  warning: "bg-yellow-900/30 text-yellow-400 border-yellow-700",
  info: "bg-blue-900/30 text-blue-400 border-blue-700",
};

const CATEGORY_LABELS: Record<string, string> = {
  bug: "Bug",
  security: "Security",
  performance: "Performance",
  quality: "Quality",
};

export default function ReviewPage() {
  const params = useParams();
  const id = Array.isArray(params.id) ? params.id[0] : (params.id ?? "");

  const { user, loading: authLoading } = useAuth();
  const { review: contextReview } = useReview();
  const router = useRouter();

  const [fetchedReview, setFetchedReview] = useState<Review | null>(null);
  const [fetchError, setFetchError] = useState<string | null>(null);

  // For "anon" the context supplies the review; for a UUID derive the review without
  // going through a redundant state copy (avoids setState-in-effect lint error)
  const contextMatchesId = id !== "anon" && contextReview?.id === id;
  const review =
    id === "anon" ? contextReview : contextMatchesId ? contextReview : fetchedReview;

  const needsFetch = id !== "anon" && !contextMatchesId;

  // Loading: still waiting while auth resolves or fetch hasn't returned yet
  const isLoading =
    id === "anon"
      ? authLoading
      : authLoading || (needsFetch && fetchedReview === null && fetchError === null);

  useEffect(() => {
    if (!needsFetch) return;
    if (authLoading) return;
    if (!user) {
      router.push("/auth/login");
      return;
    }

    let cancelled = false;

    api
      .get<Review>(`/api/reviews/${id}`)
      .then(({ data }) => {
        if (!cancelled) setFetchedReview(data);
      })
      .catch((err) => {
        if (cancelled) return;
        const status = err?.response?.status;
        if (status === 404) {
          setFetchError("Review not found.");
        } else if (status === 403) {
          setFetchError("You don't have access to this review.");
        } else {
          setFetchError("Failed to load review.");
        }
      });

    return () => {
      cancelled = true;
    };
  }, [id, needsFetch, user, authLoading, router]);

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-gray-400">Loading...</div>
      </div>
    );
  }

  if (fetchError || (id === "anon" && !contextReview)) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <p className="text-red-400 mb-4">
            {fetchError ?? "Review not found. Please submit a new review."}
          </p>
          <Link href="/" className="text-blue-400 hover:underline text-sm">
            Run a new review
          </Link>
        </div>
      </div>
    );
  }

  if (!review) return null;

  const scoreColor =
    review.score >= 80
      ? "text-green-400"
      : review.score >= 60
        ? "text-yellow-400"
        : "text-red-400";

  return (
    <div className="max-w-6xl mx-auto px-6 py-12">
      {/* Header */}
      <div className="mb-8">
        <div className="flex items-center gap-4 mb-4">
          <div className="flex items-end gap-1">
            <span className={`text-5xl font-bold ${scoreColor}`}>
              {review.score}
            </span>
            <span className="text-gray-500 text-xl mb-1">/100</span>
          </div>
          <span className="text-sm text-gray-400 bg-gray-800 border border-gray-700 px-3 py-1 rounded-full">
            {review.submissionType === "pr_url"
              ? "PR Review"
              : (review.language ?? "Code Paste")}
          </span>
        </div>
        <p className="text-gray-300 leading-relaxed max-w-3xl">{review.summary}</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Findings */}
        <div>
          <h2 className="text-white font-semibold text-lg mb-4">
            Findings ({review.findings.length})
          </h2>
          <div className="space-y-3">
            {review.findings.length === 0 ? (
              <p className="text-gray-500 text-sm">No issues found.</p>
            ) : (
              review.findings.map((f, i) => (
                <div
                  key={f.id ?? i}
                  className="bg-gray-900 border border-gray-700 rounded-xl p-4"
                >
                  <div className="flex items-center gap-2 mb-2 flex-wrap">
                    <span
                      className={`text-xs px-2 py-0.5 rounded-full border ${
                        SEVERITY_COLORS[f.severity] ??
                        "bg-gray-800 text-gray-400 border-gray-600"
                      }`}
                    >
                      {f.severity}
                    </span>
                    <span className="text-xs text-gray-500">
                      {CATEGORY_LABELS[f.category] ?? f.category}
                    </span>
                    {f.lineReference && (
                      <span className="text-xs text-gray-600 font-mono">
                        {f.lineReference}
                      </span>
                    )}
                  </div>
                  <p className="text-gray-300 text-sm mb-2">{f.description}</p>
                  {f.suggestedFix && (
                    <pre className="bg-gray-950 text-green-400 text-xs p-3 rounded-lg font-mono overflow-x-auto whitespace-pre-wrap">
                      {f.suggestedFix}
                    </pre>
                  )}
                </div>
              ))
            )}
          </div>
        </div>

        {/* Chat */}
        <div>
          <ChatPanel reviewId={id} />
        </div>
      </div>
    </div>
  );
}

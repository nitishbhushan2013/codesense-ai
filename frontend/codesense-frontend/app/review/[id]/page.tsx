"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/app/auth-context";
import { useReview } from "@/app/review-context";
import ReviewSummary from "@/components/ReviewSummary";
import ReviewCard from "@/components/ReviewCard";
import ChatPanel from "@/components/ChatPanel";
import api from "@/lib/api";
import type { Review } from "@/lib/types";
import type { FindingCategory, FindingSeverity } from "@/lib/types";

const CATEGORY_TABS: { key: "all" | FindingCategory; label: string }[] = [
  { key: "all", label: "All" },
  { key: "bug", label: "Bugs" },
  { key: "security", label: "Security" },
  { key: "performance", label: "Performance" },
  { key: "quality", label: "Quality" },
];

const SEVERITY_FILTERS: { key: "all" | FindingSeverity; label: string }[] = [
  { key: "all", label: "All" },
  { key: "critical", label: "Critical" },
  { key: "warning", label: "Warning" },
  { key: "info", label: "Info" },
];

export default function ReviewPage() {
  const params = useParams();
  const id = Array.isArray(params.id) ? params.id[0] : (params.id ?? "");

  const { user, loading: authLoading } = useAuth();
  const { review: contextReview } = useReview();
  const router = useRouter();

  const [fetchedReview, setFetchedReview] = useState<Review | null>(null);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [activeCategory, setActiveCategory] = useState<"all" | FindingCategory>("all");
  const [activeSeverity, setActiveSeverity] = useState<"all" | FindingSeverity>("all");

  const contextMatchesId = id !== "anon" && contextReview?.id === id;
  const review =
    id === "anon" ? contextReview : contextMatchesId ? contextReview : fetchedReview;

  const needsFetch = id !== "anon" && !contextMatchesId;
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

  const countByCategory = (cat: "all" | FindingCategory) =>
    cat === "all"
      ? review.findings.length
      : review.findings.filter((f) => f.category === cat).length;

  const filteredFindings = review.findings.filter((f) => {
    const catMatch = activeCategory === "all" || f.category === activeCategory;
    const sevMatch = activeSeverity === "all" || f.severity === activeSeverity;
    return catMatch && sevMatch;
  });

  return (
    <div className="max-w-6xl mx-auto px-6 py-12">
      <ReviewSummary review={review} />

      {/* Sign-up nudge for anonymous users */}
      {id === "anon" && (
        <div className="bg-blue-900/20 border border-blue-700/50 rounded-2xl p-5 mb-8 flex items-center justify-between gap-4 flex-wrap">
          <div>
            <p className="text-white font-medium text-sm">
              Save this review and chat with AI about it
            </p>
            <p className="text-blue-300 text-xs mt-0.5">
              Free account — your review history, follow-up questions, and fix suggestions all in one place.
            </p>
          </div>
          <div className="flex gap-3 flex-shrink-0">
            <Link
              href="/auth/register"
              className="bg-blue-600 hover:bg-blue-700 text-white text-sm px-4 py-2 rounded-lg font-medium transition"
            >
              Create free account
            </Link>
            <Link
              href="/auth/login"
              className="text-blue-400 hover:text-blue-300 text-sm px-4 py-2 rounded-lg border border-blue-700/50 hover:border-blue-500 transition"
            >
              Sign in
            </Link>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Findings panel */}
        <div>
          {/* Category tabs */}
          <div className="flex gap-1 bg-gray-900 border border-gray-700 rounded-xl p-1 mb-4 overflow-x-auto">
            {CATEGORY_TABS.map((tab) => {
              const count = countByCategory(tab.key);
              return (
                <button
                  key={tab.key}
                  onClick={() => setActiveCategory(tab.key)}
                  className={`flex-shrink-0 flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition ${
                    activeCategory === tab.key
                      ? "bg-blue-600 text-white"
                      : "text-gray-400 hover:text-white hover:bg-gray-800"
                  }`}
                >
                  {tab.label}
                  {count > 0 && (
                    <span
                      className={`text-xs px-1.5 py-0.5 rounded-full ${
                        activeCategory === tab.key
                          ? "bg-blue-500 text-white"
                          : "bg-gray-700 text-gray-300"
                      }`}
                    >
                      {count}
                    </span>
                  )}
                </button>
              );
            })}
          </div>

          {/* Severity filter */}
          <div className="flex items-center gap-2 mb-4">
            <span className="text-xs text-gray-500">Severity:</span>
            <div className="flex gap-1">
              {SEVERITY_FILTERS.map((sev) => (
                <button
                  key={sev.key}
                  onClick={() => setActiveSeverity(sev.key)}
                  className={`text-xs px-2.5 py-1 rounded-full border transition ${
                    activeSeverity === sev.key
                      ? "bg-gray-700 border-gray-500 text-white"
                      : "border-gray-700 text-gray-400 hover:text-white hover:border-gray-500"
                  }`}
                >
                  {sev.label}
                </button>
              ))}
            </div>
          </div>

          {/* Findings list */}
          <div className="space-y-3">
            {filteredFindings.length === 0 ? (
              <div className="text-center py-8">
                <p className="text-gray-500 text-sm">No findings match these filters.</p>
                {(activeCategory !== "all" || activeSeverity !== "all") && (
                  <button
                    onClick={() => {
                      setActiveCategory("all");
                      setActiveSeverity("all");
                    }}
                    className="text-blue-400 hover:underline text-xs mt-2"
                  >
                    Clear filters
                  </button>
                )}
              </div>
            ) : (
              filteredFindings.map((f, i) => (
                <ReviewCard key={f.id ?? i} finding={f} />
              ))
            )}
          </div>
        </div>

        {/* Chat panel */}
        <div>
          <ChatPanel reviewId={id} />
        </div>
      </div>
    </div>
  );
}

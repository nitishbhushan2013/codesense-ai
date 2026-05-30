"use client";

import { useEffect, useState } from "react";
import api from "@/lib/api";
import { Trash2, Eye } from "lucide-react";
import Link from "next/link";

interface Review {
  id: string;
  submissionType: string;
  prUrl?: string;
  language?: string;
  score?: number;
  summary?: string;
  createdAt: string;
  findings: Array<{
    id: string;
    category: string;
    severity: string;
    title: string;
  }>;
}

export default function DashboardList() {
  const [reviews, setReviews] = useState<Review[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    const fetchReviews = async () => {
      try {
        setLoading(true);
        const response = await api.get("/api/reviews");
        setReviews(response.data);
        setError(null);
      } catch {
        setError("Failed to load reviews");
        setReviews([]);
      } finally {
        setLoading(false);
      }
    };

    fetchReviews();
  }, []);

  const handleDelete = async (id: string) => {
    try {
      setDeleting(true);
      await api.delete(`/api/reviews/${id}`);
      setReviews(reviews.filter((r) => r.id !== id));
      setDeleteConfirm(null);
    } catch {
      setError("Failed to delete review");
    } finally {
      setDeleting(false);
    }
  };

  if (loading) {
    return (
      <div className="space-y-4">
        {[1, 2, 3].map((i) => (
          <div
            key={i}
            className="bg-gray-900 border border-gray-700 rounded-lg p-4 animate-pulse"
          >
            <div className="h-4 bg-gray-700 rounded w-1/4 mb-2"></div>
            <div className="h-3 bg-gray-700 rounded w-1/3"></div>
          </div>
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-900/20 border border-red-700 rounded-lg p-4 text-red-400">
        {error}
      </div>
    );
  }

  if (reviews.length === 0) {
    return (
      <div className="bg-gray-900 border border-gray-700 rounded-2xl p-16 text-center">
        <div className="text-6xl mb-4">🔍</div>
        <h2 className="text-xl font-semibold text-white mb-2">No reviews yet</h2>
        <p className="text-gray-400 mb-6">
          Submit your first PR or paste code to get an AI review
        </p>
        <Link
          href="/"
          className="inline-block bg-blue-600 hover:bg-blue-700 text-white px-6 py-3 rounded-lg font-medium transition"
        >
          Run your first review
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {reviews.map((review) => (
        <div
          key={review.id}
          data-testid="review-card"
          className="bg-gray-900 border border-gray-700 rounded-lg p-6 hover:border-gray-600 transition"
        >
          <div className="flex items-start justify-between">
            <div className="flex-1">
              <div className="flex items-center gap-3 mb-2">
                <h3 className="text-lg font-semibold text-white">
                  {review.submissionType === "pr_url" ? "PR Review" : "Code Review"}
                </h3>
                {review.score && (
                  <span className="text-sm font-medium px-2 py-1 rounded bg-blue-900/30 text-blue-400">
                    Score: {review.score}
                  </span>
                )}
              </div>
              {review.prUrl && (
                <p className="text-sm text-gray-400 mb-2 truncate">
                  {review.prUrl}
                </p>
              )}
              {review.language && (
                <p className="text-sm text-gray-400 mb-2">
                  Language: {review.language}
                </p>
              )}
              <p className="text-xs text-gray-500">
                {new Date(review.createdAt).toLocaleDateString()} at{" "}
                {new Date(review.createdAt).toLocaleTimeString()}
              </p>
              <p className="text-sm text-gray-400 mt-2">
                {review.findings.length} finding{review.findings.length !== 1 ? "s" : ""}
              </p>
            </div>

            <div className="flex items-center gap-2">
              <Link
                href={`/review/${review.id}`}
                className="p-2 text-gray-400 hover:text-blue-400 hover:bg-gray-800 rounded transition"
                title="View review"
              >
                <Eye className="w-5 h-5" />
              </Link>
              <button
                onClick={() => setDeleteConfirm(review.id)}
                className="p-2 text-gray-400 hover:text-red-400 hover:bg-gray-800 rounded transition"
                title="Delete review"
              >
                <Trash2 className="w-5 h-5" />
              </button>
            </div>
          </div>

          {deleteConfirm === review.id && (
            <div className="mt-4 pt-4 border-t border-gray-700 flex items-center gap-3">
              <p className="text-sm text-gray-400">Delete this review?</p>
              <button
                onClick={() => handleDelete(review.id)}
                disabled={deleting}
                className="text-sm px-3 py-1 bg-red-600 hover:bg-red-700 text-white rounded transition disabled:opacity-50"
              >
                {deleting ? "Deleting..." : "Delete"}
              </button>
              <button
                onClick={() => setDeleteConfirm(null)}
                disabled={deleting}
                className="text-sm px-3 py-1 bg-gray-700 hover:bg-gray-600 text-white rounded transition disabled:opacity-50"
              >
                Cancel
              </button>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}

"use client";

import { useState } from "react";
import api from "@/lib/api";
import type { SubmissionRequest } from "@/lib/types";

type Tab = "pr_url" | "paste";

const LANGUAGES = [
  "JavaScript",
  "TypeScript",
  "Python",
  "Java",
  "Go",
  "Rust",
  "C#",
  "C++",
  "Ruby",
  "PHP",
];

const PR_URL_REGEX = /^https:\/\/github\.com\/[\w.-]+\/[\w.-]+\/pull\/\d+$/;

export default function SubmitForm() {
  const [tab, setTab] = useState<Tab>("pr_url");
  const [prUrl, setPrUrl] = useState("");
  const [code, setCode] = useState("");
  const [language, setLanguage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const switchTab = (next: Tab) => {
    setTab(next);
    setError("");
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    let payload: SubmissionRequest;
    if (tab === "pr_url") {
      const trimmed = prUrl.trim();
      if (!PR_URL_REGEX.test(trimmed)) {
        setError(
          "Enter a valid GitHub PR URL (e.g. https://github.com/owner/repo/pull/123)",
        );
        return;
      }
      payload = { submissionType: "pr_url", prUrl: trimmed };
    } else {
      if (!code.trim()) {
        setError("Paste some code to review");
        return;
      }
      if (!language) {
        setError("Select a language");
        return;
      }
      payload = { submissionType: "paste", code, language };
    }

    setLoading(true);
    try {
      await api.post("/api/reviews", payload);
      // Navigation to /review/[id] is wired up in STORY-205.
    } catch (err: any) {
      setError(
        err.response?.data?.message ||
          "Review engine is not yet available. Please try again soon.",
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full max-w-2xl mx-auto">
      <div className="bg-gray-900 border border-gray-700 rounded-2xl p-6 md:p-8">
        {/* Tabs */}
        <div className="flex bg-gray-800 border border-gray-700 rounded-lg p-1 mb-6">
          <button
            type="button"
            onClick={() => switchTab("pr_url")}
            className={`flex-1 py-2 px-4 rounded-md text-sm font-medium transition ${
              tab === "pr_url"
                ? "bg-blue-600 text-white"
                : "text-gray-400 hover:text-white"
            }`}
          >
            GitHub PR URL
          </button>
          <button
            type="button"
            onClick={() => switchTab("paste")}
            className={`flex-1 py-2 px-4 rounded-md text-sm font-medium transition ${
              tab === "paste"
                ? "bg-blue-600 text-white"
                : "text-gray-400 hover:text-white"
            }`}
          >
            Paste Code
          </button>
        </div>

        {/* Error */}
        {error && (
          <div className="bg-red-900/50 border border-red-700 text-red-300 px-4 py-3 rounded-lg mb-4 text-sm">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {tab === "pr_url" ? (
            <div>
              <label className="block text-sm text-gray-400 mb-1">
                Pull Request URL
              </label>
              <input
                type="url"
                value={prUrl}
                onChange={(e) => setPrUrl(e.target.value)}
                placeholder="https://github.com/owner/repo/pull/123"
                className="w-full bg-gray-800 border border-gray-600 text-white px-4 py-3 rounded-lg focus:outline-none focus:border-blue-500 transition"
                required
              />
              <p className="text-xs text-gray-500 mt-2">
                Public repositories only for now.
              </p>
            </div>
          ) : (
            <>
              <div>
                <label className="block text-sm text-gray-400 mb-1">
                  Language
                </label>
                <select
                  value={language}
                  onChange={(e) => setLanguage(e.target.value)}
                  className="w-full bg-gray-800 border border-gray-600 text-white px-4 py-3 rounded-lg focus:outline-none focus:border-blue-500 transition"
                  required
                >
                  <option value="">Select a language</option>
                  {LANGUAGES.map((lang) => (
                    <option key={lang} value={lang}>
                      {lang}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm text-gray-400 mb-1">Code</label>
                <textarea
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  placeholder="Paste your code here..."
                  rows={12}
                  className="w-full bg-gray-800 border border-gray-600 text-white px-4 py-3 rounded-lg font-mono text-sm focus:outline-none focus:border-blue-500 transition resize-y"
                  required
                />
                <p className="text-xs text-gray-500 mt-2">
                  {code.length.toLocaleString()} characters
                </p>
              </div>
            </>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 hover:bg-blue-700 disabled:bg-blue-800 disabled:cursor-not-allowed text-white py-3 rounded-lg font-medium transition flex items-center justify-center gap-2"
          >
            {loading ? (
              <>
                <svg
                  className="animate-spin h-5 w-5"
                  viewBox="0 0 24 24"
                  fill="none"
                >
                  <circle
                    cx="12"
                    cy="12"
                    r="10"
                    stroke="currentColor"
                    strokeWidth="3"
                    className="opacity-25"
                  />
                  <path
                    d="M4 12a8 8 0 018-8"
                    stroke="currentColor"
                    strokeWidth="3"
                    strokeLinecap="round"
                    className="opacity-75"
                  />
                </svg>
                Reviewing...
              </>
            ) : (
              "Review my code"
            )}
          </button>
        </form>
      </div>
    </div>
  );
}

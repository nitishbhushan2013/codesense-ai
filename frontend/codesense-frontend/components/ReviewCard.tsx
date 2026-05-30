"use client";

import { useState } from "react";
import type { Finding } from "@/lib/types";

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

export default function ReviewCard({ finding }: { finding: Finding }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(finding.suggestedFix).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  return (
    <div className="bg-gray-900 border border-gray-700 rounded-xl p-4">
      <div className="flex items-center gap-2 mb-2 flex-wrap">
        <span
          className={`text-xs px-2 py-0.5 rounded-full border font-medium ${
            SEVERITY_COLORS[finding.severity] ??
            "bg-gray-800 text-gray-400 border-gray-600"
          }`}
        >
          {finding.severity}
        </span>
        <span className="text-xs text-gray-500">
          {CATEGORY_LABELS[finding.category] ?? finding.category}
        </span>
        {finding.lineReference && (
          <span className="text-xs text-gray-600 font-mono ml-auto">
            {finding.lineReference}
          </span>
        )}
      </div>

      <p className="text-gray-300 text-sm mb-3">{finding.description}</p>

      {finding.suggestedFix && (
        <div className="relative group">
          <pre className="bg-gray-950 text-green-400 text-xs p-3 pr-16 rounded-lg font-mono overflow-x-auto whitespace-pre-wrap">
            {finding.suggestedFix}
          </pre>
          <button
            onClick={handleCopy}
            title="Copy fix"
            className="absolute top-2 right-2 text-xs px-2 py-1 rounded bg-gray-800 text-gray-400 hover:bg-gray-700 hover:text-white transition opacity-0 group-hover:opacity-100 focus:opacity-100"
          >
            {copied ? "Copied!" : "Copy"}
          </button>
        </div>
      )}
    </div>
  );
}

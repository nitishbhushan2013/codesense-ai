"use client";

import { useState } from "react";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { oneDark } from "react-syntax-highlighter/dist/esm/styles/prism";
import type { Finding, FindingSeverity } from "@/lib/types";

const SEVERITY_STYLES: Record<FindingSeverity, string> = {
  critical: "bg-red-900/50 border-red-700 text-red-300",
  warning: "bg-yellow-900/40 border-yellow-700 text-yellow-300",
  info: "bg-blue-900/40 border-blue-700 text-blue-300",
};

const SEVERITY_LABEL: Record<FindingSeverity, string> = {
  critical: "Critical",
  warning: "Warning",
  info: "Info",
};

export default function ReviewCard({
  finding,
  language,
}: {
  finding: Finding;
  language?: string | null;
}) {
  const [copied, setCopied] = useState(false);

  const severity = SEVERITY_STYLES[finding.severity]
    ? finding.severity
    : "info";

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(finding.suggestedFix);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard unavailable (e.g. insecure context) — leave the label as-is.
    }
  };

  return (
    <div
      data-testid="review-card"
      data-category={finding.category}
      data-severity={finding.severity}
      className="bg-gray-900 border border-gray-700 rounded-xl p-5"
    >
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex items-center gap-2 flex-wrap">
          <span
            className={`text-xs font-medium px-2 py-0.5 rounded border ${SEVERITY_STYLES[severity]}`}
          >
            {SEVERITY_LABEL[severity]}
          </span>
          {finding.lineReference && (
            <span className="text-xs text-gray-400 font-mono">
              {finding.lineReference}
            </span>
          )}
        </div>
      </div>

      <p className="text-gray-200 text-sm mb-4 whitespace-pre-wrap">
        {finding.description}
      </p>

      {finding.suggestedFix && (
        <div>
          <div className="flex items-center justify-between mb-1">
            <span className="text-xs uppercase tracking-wide text-gray-500">
              Suggested fix
            </span>
            <button
              type="button"
              onClick={handleCopy}
              className="text-xs text-gray-400 hover:text-white transition px-2 py-1 rounded border border-gray-700 hover:border-gray-500"
            >
              {copied ? "Copied!" : "Copy"}
            </button>
          </div>
          <div className="rounded-lg overflow-hidden border border-gray-700 text-sm">
            <SyntaxHighlighter
              language={(language || "text").toLowerCase()}
              style={oneDark}
              customStyle={{ margin: 0, background: "#0d1117" }}
              wrapLongLines
            >
              {finding.suggestedFix}
            </SyntaxHighlighter>
          </div>
        </div>
      )}
    </div>
  );
}

"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/app/auth-context";
import type { ChatMessage } from "@/lib/types";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export default function ChatPanel({ reviewId }: { reviewId: string }) {
  const { user, loading } = useAuth();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!user || reviewId === "anon") return;

    fetch(`${API_URL}/api/reviews/${reviewId}/chat`, { credentials: "include" })
      .then((res) => (res.ok ? res.json() : Promise.reject(res.status)))
      .then((data: ChatMessage[]) => setMessages(data))
      .catch(() => {});
  }, [user, reviewId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const sendMessage = async () => {
    const text = input.trim();
    if (!text || isStreaming) return;

    setInput("");
    setError(null);

    const streamId = `stream-${Date.now()}`;

    setMessages((prev) => [
      ...prev,
      { id: `local-${Date.now()}`, role: "user", content: text, createdAt: new Date().toISOString() },
      { id: streamId, role: "assistant", content: "", createdAt: new Date().toISOString() },
    ]);
    setIsStreaming(true);

    try {
      const response = await fetch(`${API_URL}/api/reviews/${reviewId}/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ message: text }),
      });

      if (!response.ok || !response.body) {
        throw new Error(`HTTP ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split("\n\n");
        buffer = parts.pop() ?? "";

        for (const part of parts) {
          const lines = part.split("\n");
          const eventLine = lines.find((l) => l.startsWith("event:"));
          const dataLine = lines.find((l) => l.startsWith("data:"));

          if (eventLine?.includes("error")) {
            throw new Error("The AI encountered an error. Please try again.");
          }

          if (dataLine) {
            const chunk = dataLine.slice(5).trim();
            if (chunk) {
              setMessages((prev) => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                if (last?.role === "assistant") {
                  updated[updated.length - 1] = {
                    ...last,
                    content: last.content + chunk,
                  };
                }
                return updated;
              });
            }
          }
        }
      }
    } catch (err) {
      const msg =
        err instanceof Error ? err.message : "Failed to send message. Please try again.";
      setError(msg);
      setMessages((prev) => prev.filter((m) => m.id !== streamId));
    } finally {
      setIsStreaming(false);
    }
  };

  if (loading) return null;

  if (!user) {
    return (
      <div className="bg-gray-900 border border-gray-700 rounded-2xl p-8 text-center">
        <p className="text-gray-400 mb-4">
          Sign in to chat with the AI about this review
        </p>
        <Link
          href="/auth/login"
          className="inline-block bg-blue-600 hover:bg-blue-700 text-white px-5 py-2 rounded-lg text-sm font-medium transition"
        >
          Sign in
        </Link>
      </div>
    );
  }

  return (
    <div
      className="bg-gray-900 border border-gray-700 rounded-2xl flex flex-col"
      style={{ height: "520px" }}
    >
      <div className="px-6 py-4 border-b border-gray-700 flex-shrink-0">
        <h2 className="text-white font-semibold">Chat with AI</h2>
        <p className="text-gray-400 text-sm">Ask follow-up questions about this review</p>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-4 min-h-0">
        {messages.length === 0 && (
          <p className="text-center text-gray-500 text-sm pt-8">
            Ask a question about this code review
          </p>
        )}
        {messages.map((msg, i) => (
          <div
            key={msg.id || i}
            className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
          >
            <div
              className={`max-w-[80%] px-4 py-2 rounded-2xl text-sm whitespace-pre-wrap ${
                msg.role === "user"
                  ? "bg-blue-600 text-white rounded-tr-sm"
                  : "bg-gray-800 text-gray-200 rounded-tl-sm"
              }`}
            >
              {msg.content ||
                (isStreaming && msg.role === "assistant" ? (
                  <span className="inline-flex gap-1 items-center h-4">
                    <span className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce" />
                    <span className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce [animation-delay:0.15s]" />
                    <span className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce [animation-delay:0.3s]" />
                  </span>
                ) : null)}
            </div>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>

      {error && (
        <p className="px-4 pb-1 text-red-400 text-xs flex-shrink-0">{error}</p>
      )}

      <div className="px-4 pb-4 pt-2 border-t border-gray-700 flex-shrink-0">
        <div className="flex gap-2">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
              }
            }}
            placeholder="Ask a question..."
            disabled={isStreaming}
            aria-label="Chat message input"
            className="flex-1 bg-gray-800 border border-gray-600 text-white px-4 py-2 rounded-lg text-sm focus:outline-none focus:border-blue-500 disabled:opacity-50 transition"
          />
          <button
            onClick={sendMessage}
            disabled={isStreaming || !input.trim()}
            className="bg-blue-600 hover:bg-blue-700 disabled:bg-gray-700 disabled:cursor-not-allowed text-white px-4 py-2 rounded-lg text-sm font-medium transition"
          >
            {isStreaming ? "..." : "Send"}
          </button>
        </div>
      </div>
    </div>
  );
}

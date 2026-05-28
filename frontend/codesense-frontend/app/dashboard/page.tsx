"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/app/auth-context";

export default function DashboardPage() {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) {
      router.push("/auth/login");
    }
  }, [user, loading, router]);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-gray-400">Loading...</div>
      </div>
    );
  }

  if (!user) return null;

  return (
    <div className="max-w-6xl mx-auto px-6 py-12">
      {/* Header */}
      <div className="mb-10">
        <h1 className="text-3xl font-bold text-white mb-2">
          Welcome back, {user.name} 👋
        </h1>
        <p className="text-gray-400">
          Your AI code review history will appear here.
        </p>
      </div>

      {/* Empty state */}
      <div className="bg-gray-900 border border-gray-700 rounded-2xl p-16 text-center">
        <div className="text-6xl mb-4">🔍</div>
        <h2 className="text-xl font-semibold text-white mb-2">
          No reviews yet
        </h2>
        <p className="text-gray-400 mb-6">
          Submit your first PR or paste code to get an AI review
        </p>
        <a
          href="/"
          className="inline-block bg-blue-600 hover:bg-blue-700 text-white px-6 py-3 rounded-lg font-medium transition"
        >
          Run your first review
        </a>
      </div>
    </div>
  );
}

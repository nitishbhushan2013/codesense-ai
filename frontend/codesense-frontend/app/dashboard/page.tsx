"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/app/auth-context";
import DashboardList from "@/components/DashboardList";

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
          Your AI code review history
        </p>
      </div>

      <DashboardList />
    </div>
  );
}

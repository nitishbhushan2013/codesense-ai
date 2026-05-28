"use client";

import Link from "next/link";
import { useAuth } from "@/app/auth-context";

export default function Navbar() {
  const { user, loading, logout } = useAuth();

  return (
    <nav className="bg-gray-900 border-b border-gray-700 px-6 py-4">
      <div className="max-w-6xl mx-auto flex items-center justify-between">
        {/* Logo */}
        <Link href="/" className="text-white font-bold text-xl">
          🔍 CodeSense AI
        </Link>

        {/* Right side */}
        <div className="flex items-center gap-4">
          {loading ? (
            <div className="w-8 h-8 rounded-full bg-gray-700 animate-pulse" />
          ) : user ? (
            <>
              <Link
                href="/dashboard"
                className="text-gray-300 hover:text-white text-sm transition"
              >
                Dashboard
              </Link>
              <div className="flex items-center gap-3">
                {user.avatarUrl && (
                  <img
                    src={user.avatarUrl}
                    alt={user.name}
                    className="w-8 h-8 rounded-full border border-gray-600"
                  />
                )}
                <span className="text-gray-300 text-sm">{user.name}</span>
                <button
                  onClick={logout}
                  className="text-gray-400 hover:text-white text-sm transition"
                >
                  Logout
                </button>
              </div>
            </>
          ) : (
            <>
              <Link
                href="/auth/login"
                className="text-gray-300 hover:text-white text-sm transition"
              >
                Login
              </Link>
              <Link
                href="/auth/register"
                className="bg-blue-600 hover:bg-blue-700 text-white text-sm px-4 py-2 rounded-lg transition"
              >
                Sign Up
              </Link>
            </>
          )}
        </div>
      </div>
    </nav>
  );
}

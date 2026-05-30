"use client";

import React, { createContext, useContext, useEffect, useState } from "react";
import api from "@/lib/api";

interface User {
  id: string;
  name: string;
  email: string;
  avatarUrl?: string;
}

interface AuthContextType {
  user: User | null;
  loading: boolean;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  loading: true,
  logout: async () => {},
  refreshUser: async () => {},
});

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  const refreshUser = async () => {
    try {
      const response = await api.get("/api/auth/me");
      setUser(response.data);
    } catch {
      setUser(null);
    } finally {
      setLoading(false);
    }
  };

  const logout = async () => {
    try {
      await api.post("/api/auth/logout");
      setUser(null);
      window.location.href = "/";
    } catch {
      setUser(null);
    }
  };

  useEffect(() => {
    const load = async () => {
      try {
        const response = await api.get("/api/auth/me");
        setUser(response.data);
      } catch {
        setUser(null);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);

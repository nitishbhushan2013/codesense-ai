"use client";

import React, { createContext, useContext } from "react";

type ReviewContextType = Record<string, never>;

const ReviewContext = createContext<ReviewContextType>({});

export function ReviewProvider({ children }: { children: React.ReactNode }) {
  return (
    <ReviewContext.Provider value={{}}>
      {children}
    </ReviewContext.Provider>
  );
}

export const useReview = () => useContext(ReviewContext);

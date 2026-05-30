"use client";

import React, { createContext, useContext, useState } from "react";
import type { Review } from "@/lib/types";

type ReviewContextType = {
  review: Review | null;
  setReview: (r: Review | null) => void;
};

const ReviewContext = createContext<ReviewContextType>({
  review: null,
  setReview: () => {},
});

export function ReviewProvider({ children }: { children: React.ReactNode }) {
  const [review, setReview] = useState<Review | null>(null);
  return (
    <ReviewContext.Provider value={{ review, setReview }}>
      {children}
    </ReviewContext.Provider>
  );
}

export const useReview = () => useContext(ReviewContext);

"use client";

import React, { createContext, useContext, useState } from "react";
import type { Review } from "@/lib/types";

/**
 * Holds the most recently submitted review so the results page can render it
 * without a refetch.
 *
 * This matters because of how the backend behaves (see ReviewService in the
 * root CLAUDE.md):
 *   - Anonymous reviews are EPHEMERAL: POST /api/reviews returns the full
 *     review with `id: null` and it is NEVER persisted. There is no id to
 *     fetch by, so the only source of truth is the object returned by the POST.
 *   - Persisted (logged-in) reviews do have an id, but GET /api/reviews/{id}
 *     does not exist yet (STORY-301 backend scope). So even for those, a direct
 *     load / refresh of /review/{id} cannot refetch — see the TODO on the page.
 *
 * SubmitForm calls `setReview(...)` with the POST response, then navigates to
 * /review/<id|anon>. The page reads it back from here.
 */
interface ReviewContextType {
  review: Review | null;
  setReview: (review: Review | null) => void;
}

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

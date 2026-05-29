# STORY-203 — Claude AI Review Service

**Status:** ✅ Complete
**Sprint:** 2 — Core Value
**Layer:** Backend service

---

## 1. Story intention

Build the "brain" of CodeSense — a single service that turns a code string
into a structured AI review.

This is the core value of the product. Every other story (PR fetching,
controller, results page, chat) feeds into or out of this service. If this
doesn't work, nothing else matters.

The story is intentionally narrow: just the service layer. No controller,
no persistence, no end-user flow. The shape we ship here defines what
STORY-204 wraps in an HTTP endpoint.

### Done when

> Given a code string, `ClaudeService` returns a parsed `ReviewResponse`
> with findings.

Testable with a mocked HTTP layer. We do not need to hit the real Claude
API to satisfy the DoD — saves money and makes the test deterministic.

---

## 2. Thinking strategy

The starting question: **what is the smallest, most testable unit that
captures "ask Claude to review code"?**

Three things have to be right:

1. **The prompt.** The architecture doc already specified a system prompt
   with an exact JSON schema. I copied it verbatim — divergence between
   the doc and the code is technical debt I don't want.
2. **The HTTP call.** Anthropic's `/v1/messages` endpoint with the right
   headers (`x-api-key`, `anthropic-version`).
3. **The parsing.** Claude returns its answer inside a JSON envelope:
   `{"content": [{"type": "text", "text": "<our JSON>"}]}`. Two layers
   of parsing: the envelope (always JSON), and the inner text (should be
   JSON, may not be).

What I deliberately did NOT think about:
- Streaming responses (out of scope; chat does that in STORY-302)
- Caching identical prompts (premature; no evidence we need it yet)
- Multi-turn conversation (chat is a separate epic)
- Token counting / cost guards (a real product concern but not blocking MVP)

### What I needed to learn before writing code

- The existing service style (constructor injection, Lombok, `@Slf4j`)
  → read `AuthService.java`
- The reusable test pattern for `WebClient` from STORY-202
  → reuse `ExchangeFunction` stubbing
- Anthropic's API contract → known from prior work; documented inline
- The architecture's prompt → already in `ARCHITECTURE.md`

---

## 3. Design considerations

### Typed errors via enum

A single `ClaudeApiException` with a `Reason` enum, rather than a hierarchy
of exception classes (`ClaudeAuthException`, `ClaudeRateLimitException`,
etc.).

**Why:** Enums are easier to switch on, easier to extend, and the retry
decision becomes a property of the enum itself (`isRetryable()`). A class
hierarchy would require either visitor pattern or `instanceof` chains —
both worse.

**Tradeoff:** Less idiomatic in some Java circles. Accepted because the
codebase is consistent (see `GitHubFetchException` from STORY-202 —
same pattern).

### Retry only what's transient

The retry loop asks the exception "are you retryable?" instead of checking
HTTP status codes. The enum says:

| Reason         | Retryable | Rationale                                  |
| -------------- | --------- | ------------------------------------------ |
| `AUTH_ERROR`   | no        | Same key will fail the same way            |
| `BAD_REQUEST`  | no        | Same payload will fail the same way        |
| `RATE_LIMITED` | yes       | Time fixes it                              |
| `SERVER_ERROR` | yes       | Anthropic-side hiccup                      |
| `PARSE_ERROR`  | yes       | LLMs are stochastic; another roll may work |
| `UNAVAILABLE`  | yes       | Network hiccup                             |

PARSE_ERROR being retryable is a judgment call. If Claude consistently
returns garbage, we'll burn retries — but that pattern would indicate
a prompt problem, which is a higher-order fix.

### Defensive parsing — strip markdown fences

Claude is told to return raw JSON. LLMs occasionally wrap output in
` ```json ... ``` ` anyway. Cheap to strip, cheap to test, prevents an
entire class of parse failures.

This is "trust but verify" applied to model output.

### Externalize timing for tests

`retry-backoff-ms` is a config value, not a constant. The test sets it
to `0` so the suite runs fast. The default of 500ms gives real callers
sensible exponential backoff (500ms → 1s).

This is a small choice that pays dividends — without it, the retry tests
take ~1.5s each. With it, they're sub-millisecond.

### Imperative retry loop (not Reactor `.retryWhen`)

WebClient is reactive; we could chain `.retryWhen(Retry.backoff(...))`
into the Mono. I chose a `for` loop with try/catch.

**Why:** The codebase uses `.block()` everywhere (Spring MVC, not WebFlux).
Adding reactive retry semantics for one method introduces a mental model
mismatch. Imperative wins for readability when the rest is imperative.

### API key handling

Default `${CLAUDE_API_KEY:}` — blank if env var unset. The service throws
`AUTH_ERROR` immediately if blank, before making any network call. This
prevents accidental calls with placeholder strings and surfaces the
config issue at the first request, not the 50th log entry.

Aligns with STORY-108 (secrets hygiene) — every secret in the repo is
moving to this pattern.

---

## 4. Development strategy

Order I built things:

1. **DTOs first.** `FindingDto` and `ReviewResponse` are pure data — no
   dependencies, no logic. Defining them first locks the contract.
2. **Exception type.** Defines the error vocabulary the service speaks.
3. **Service skeleton.** Constructor, `analyzeCode` signature, the
   retry-loop scaffold with stubbed `callClaude` returning `""`.
4. **Prompt construction.** System + user prompt assembly. Verified
   against the architecture doc.
5. **HTTP call + envelope parsing.** `WebClient` setup, `extractText`,
   `mapHttpError`. Tested compilation only at this point.
6. **Inner parsing.** `parseReview` + `stripCodeFences`.
7. **Tests.** Once happy-path compiled, I wrote tests in this order:
   happy path → markdown fences → retries succeed → retries exhaust →
   no-retry auth → parse error → missing key. Each test informed any
   missing edge case in the service.
8. **Config + backlog update** last.

What I deferred:
- **Storage of the review** (deferred to STORY-204, where the
  controller decides whether to persist for logged-in users vs. return
  ephemeral for anonymous).
- **Score validation** (e.g. clamping 0-100). The DTO accepts anything;
  if Claude returns 150, we pass it through. Premature to enforce here.
- **Prompt length guard** (very long code might exceed context window).
  Will handle when we see it.

---

## 5. Technical plan

| Step | File                                    | Action                                          |
| ---- | --------------------------------------- | ----------------------------------------------- |
| 1    | `dto/FindingDto.java`                   | New record matching architecture schema         |
| 2    | `dto/ReviewResponse.java`               | New record with score + summary + findings list |
| 3    | `exception/ClaudeApiException.java`     | New exception with `Reason` enum                |
| 4    | `service/ClaudeService.java`            | New service with retry loop                     |
| 5    | `test/.../ClaudeServiceTest.java`       | Seven unit tests                                |
| 6    | `application.yml`                       | Env-var key, max-tokens, retry-backoff-ms       |
| 7    | `docs/STORY-BACKLOG.md`                 | Mark STORY-203 complete                         |
| 8    | `docs/stories/STORY-203.md`             | This document                                   |

---

## 6. As-built

**Files added:**

- `backend/codesense-backend/src/main/java/com/codesense/dto/FindingDto.java`
- `backend/codesense-backend/src/main/java/com/codesense/dto/ReviewResponse.java`
- `backend/codesense-backend/src/main/java/com/codesense/exception/ClaudeApiException.java`
- `backend/codesense-backend/src/main/java/com/codesense/service/ClaudeService.java`
- `backend/codesense-backend/src/test/java/com/codesense/service/ClaudeServiceTest.java`

**Files modified:**

- `backend/codesense-backend/src/main/resources/application.yml`
- `docs/STORY-BACKLOG.md`

**Tests:** 7/7 pass in ~0.9s.

| Test                                        | Verifies                                    |
| ------------------------------------------- | ------------------------------------------- |
| `parsesValidResponse`                       | Happy path — Claude → ReviewResponse        |
| `parsesResponseWrappedInMarkdownFences`     | Defensive: ` ```json...``` ` wrapper        |
| `retriesOnRateLimitThenSucceeds`            | 429, 429, 200 → succeeds (3 calls)          |
| `givesUpAfterMaxRetries`                    | 429, 429, 429 → throws (exactly 3 calls)    |
| `doesNotRetryOnAuthError`                   | 401 → throws after 1 call (no retry)        |
| `throwsParseErrorWhenInnerJsonIsInvalid`    | Claude returned non-JSON → PARSE_ERROR      |
| `throwsAuthErrorWhenApiKeyMissing`          | Blank key → AUTH_ERROR before network call  |

---

## 7. Followups / known debt

- **No real Claude integration test.** Verified only with mocked HTTP.
  Acceptable per DoD. A `@Disabled` integration test pointing at the
  real API could be added under a profile flag for occasional smoke tests.
- **Model is `claude-sonnet-4-20250514`** — older than what's available
  (Sonnet 4.6 / Opus 4.7). Upgrade is a one-line config change in
  `application.yml`. Out of scope here.
- **Token cost is unbounded.** Long PR diffs could rack up cost. Add a
  prompt-size guard in a future story if we see it happening.
- **STORY-108 (secrets hygiene)** depends on the env-var pattern this
  story established. The pattern is identical for `GITHUB_API_TOKEN`,
  `JWT_SECRET`, `GITHUB_CLIENT_SECRET`, `DB_PASSWORD`.
- **STORY-204** wires `ClaudeService` into the `POST /api/reviews`
  controller and decides persistence vs. ephemeral for logged-in vs.
  anonymous callers.

---

## Related

- [[STORY-202]] — provides the diff that this service consumes for PR submissions
- [[STORY-204]] — wraps this service in an HTTP endpoint
- [[STORY-108]] — secrets hygiene, blocks production use of this service
- Architecture doc: `docs/ARCHITECTURE.md` § "Claude API Prompt Design"

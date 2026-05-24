# Incident Copilot

An SRE incident-management backend, with a small React dashboard on top, that turns a flood of raw monitoring alerts into a handful of correlated incidents, has an LLM do grounded root-cause analysis against a company's own historical postmortems, and writes every resolved incident's postmortem straight back into that same knowledge base — so the next incident like it gets a specific, grounded answer instead of a generic one.

The project exists to work through problems that are genuinely hard, not just CRUD with an LLM call bolted on:

1. Deciding, under real concurrency, whether an incoming alert belongs to an already-open incident or has to start a new one. Two alerts landing in the same instant must not both decide "there's no existing incident yet" and each create their own.
2. Treating an external LLM as a first-class unreliable dependency rather than an afterthought. It will time out, get rate-limited, or just be down sometimes, and the system has to degrade to something an on-call engineer can still use, not block or crash.
3. Making the retrieval-augmented part of the "AI" actually verifiable instead of trusted on faith — proving, per analysis, which historical postmortem (if any) the model was actually grounded in.
4. Delivering a notification about the outcome reliably, even though "analysis finished" and "notification sent" are not the same transaction, and the second one talks to an external system that can fail on its own.

## What it actually does

An ops engineer picks one of four seeded incident scenarios — a database connection pool exhaustion, a memory leak, an auth token rotation gone wrong, a payment gateway outage — and fires a burst of alerts for it. Not one repeated line: each scenario cycles through three differently-worded symptoms, the way a real outage actually looks scrolling down a monitoring dashboard. Those alerts get embedded and correlated into one or more incidents behind the scenes.

An on-call engineer opens the resulting incident, looks at the raw correlated alerts, and clicks Analyze. The backend embeds a summary of those alerts, runs a similarity search against every stored postmortem — both ones seeded ahead of time and ones written by resolving earlier incidents — and hands the closest matches to Gemini alongside the alerts as grounding context. What comes back is a likely root cause and concrete next steps. Separately, the UI shows exactly which historical postmortem titles were retrieved for that specific analysis, so you can actually check the model isn't just making something up.

If Gemini is down, rate-limited, or times out, the analysis still completes — it degrades to a result built directly from the raw correlated alerts, so the on-call engineer is never left staring at a spinner or an error page. Either way, a notification gets queued and delivered, retried with backoff if the delivery itself fails.

When the engineer resolves the incident, they fill in a short structured postmortem — root cause category, impact, what happened, root cause detail, resolution steps, follow-up actions — and that write-up is embedded and stored exactly the same way a seeded postmortem is. It isn't just an audit record. It becomes retrievable context for the next incident that looks like this one. That's the actual point of the whole exercise: the system's answers get more specific as more incidents get resolved through it, without retraining anything.

## Architecture

Backend: Java 17, Spring Boot 4 / Spring Framework 7, Spring Security with JWT, Spring Data JPA, PostgreSQL with the pgvector extension, Flyway for migrations, Spring AI for the Gemini integration (both chat and embeddings), Resilience4j for circuit breaking and rate limiting, Testcontainers for integration tests against a real Postgres, Maven for the build.

Frontend: React with Vite and Tailwind v4. No router — there are exactly four views (login, incident list, incident detail, an ops console) and plain `useState` is enough to switch between them.

Deployment: a Dockerfile and a Render blueprint (`render.yaml`), plus a `docker-compose.full.yml` that builds the app image and runs it alongside a real Postgres container, so the whole thing can be verified end to end before deploying anything for real.

Entities reference each other by plain UUID fields, not JPA relationships. An `Alert` has an `incidentId`, an `AnalysisRequest` has an `incidentId`, a `Postmortem` has an optional `sourceIncidentId` — nothing is `@ManyToOne`. Foreign key constraints still exist at the database level; the object graph in Java just stays flat, and every table can be reasoned about independently, which matters once alerts are landing concurrently from more than one source.

One deliberate exception to "just use JPA": vector embeddings never appear as a field on `Alert` or `Postmortem` at all. The database columns exist (`alerts.embedding`, `postmortems.embedding`, both `vector(768)`), but Hibernate never sees them — they're read and written entirely through dedicated `JdbcTemplate`-based repositories (`AlertEmbeddingRepository`, `PostmortemEmbeddingRepository`) using pgvector's own `?::vector` cast literals. Letting Hibernate try to map a pgvector column directly has a long tail of fragile edge cases; keeping it out of the entity graph entirely sidesteps all of them.

### Domain model

| Entity | Purpose |
|---|---|
| `User` | Login identity. Role is `USER` or `ADMIN` — `USER` is the on-call engineer persona (investigate, analyze, resolve, write postmortems), `ADMIN` is the ops console (trigger scenarios, watch system health). |
| `Alert` | One raw monitoring alert: source system, severity, title, raw payload. Embedded and correlated into an incident on ingest. |
| `Incident` | A correlated group of alerts. Tracks status (`OPEN` / `INVESTIGATING` / `RESOLVED`), correlated alert count, and — once resolved — the root cause summary written at resolution time. |
| `Postmortem` | A write-up, either seeded ahead of time or written when resolving an incident (`sourceIncidentId` records which, if either). Embedded and retrievable by every future analysis. |
| `AnalysisRequest` | One request to analyze an incident. Tracks status (`PENDING → RUNNING → COMPLETED` / `DEGRADED` / `FAILED`), the result summary, and which postmortem titles were actually retrieved and used. |
| `LlmUsageLog` | One row per attempted LLM call, whatever the outcome — `SUCCESS`, `CIRCUIT_OPEN`, `RATE_LIMITED`, `TIMEOUT`, `ERROR`. The audit trail behind the ops console's usage log. |
| `OutboundNotification` | One notification queued after an analysis completes. Delivered asynchronously outside the analysis transaction, retried with exponential backoff on failure. |

## How the AI actually works

### Correlating alerts into incidents

Every alert gets embedded, via Gemini's embedding model truncated to 768 dimensions, the moment it's ingested. A nearest-neighbor search then finds the closest currently open incident by cosine distance. If that distance is under a threshold (`correlation.similarity-threshold`, `0.15` by default), the alert attaches to that incident; otherwise it starts a new one.

The hard part isn't the similarity search, it's the concurrency. An alert storm fires many alerts at once, and two alerts that should genuinely both create a new incident — because there's nothing to correlate against yet — must not both look up "no existing incident" and each insert their own. A Postgres advisory lock (`pg_advisory_xact_lock`) serializes the decide-and-attach step for the duration of one transaction, so only one alert at a time gets to ask "is there an existing incident" and act on the answer. `@Version` optimistic locking is used too, but for a different problem: it catches conflicting updates to an incident row that already exists (two alerts attaching to the same incident at once), not the decision of whether to create one in the first place.

### Grounding the answer, and proving it

When an analysis runs, the backend doesn't just hand the alerts to Gemini and hope. It builds a retrieval query from the alerts, embeds it, and finds the top-K (`postmortem.retrieval.top-k`, `3` by default) nearest postmortems by cosine similarity — seeded ones and ones written by resolving past incidents, indistinguishable to the search. Those get pasted directly into the prompt as "potentially relevant historical postmortems" before asking for a root cause.

The part that actually makes this trustworthy rather than a leap of faith: every analysis records which postmortem titles were retrieved for it (`AnalysisRequest.retrievedPostmortemTitles`), surfaced in the UI as "Retrieved context." Fire the same scenario twice and resolve the first one in between, and the second analysis will actually cite the postmortem from the first resolution — and the retrieved-context field confirms it isn't a coincidence, the model was handed that specific text.

### Treating the LLM as unreliable, because it is

Every call to Gemini's chat model goes through a circuit breaker and a rate limiter, composed as functional decorators (`CircuitBreaker.decorateSupplier`, `RateLimiter.decorateSupplier`) rather than the `@CircuitBreaker` / `@RateLimiter` annotations. That's deliberate: Spring AOP only intercepts an annotated method when the call arrives through the proxy, i.e. from a different bean — call it from within the same class and the annotation is silently ignored. Decorating a supplier by hand sidesteps that trap entirely and makes the protection explicit at the call site instead of implicit and easy to defeat by accident. A timeout is enforced the same way, via a bounded `CompletableFuture.get(timeout, unit)` rather than `@TimeLimiter`, which needs an async return type this call doesn't have.

Every attempt, successful or not, gets exactly one `LlmUsageLog` row — that's what the ops console's usage log and outcome counts are actually reading, not a guess.

If the call fails for any reason, the analysis doesn't fail outright. It completes as `DEGRADED`, with a result built directly from the raw correlated alerts, so the on-call engineer always has something usable instead of an error page. `FAILED` is reserved for a narrower case: the incident or its alerts couldn't even be loaded in the first place, before an LLM call was ever attempted.

### Debounce without an in-memory timer

Analysis doesn't have to be requested manually. A scheduled sweep (`AnalysisDebounceScheduler`) checks periodically (every `analysis.debounce-check-interval-seconds`, `10` by default) for incidents that have gone quiet — no new alert attached in the last `analysis.debounce-window-seconds`, `30` by default — and auto-submits analysis for them. That's a deliberate choice over a per-incident in-memory timer: a persisted, periodic sweep survives an app restart with no cancel/reschedule bookkeeping to get wrong, at the cost of only checking on a fixed interval rather than the instant an incident actually goes quiet.

### Notifications outside the transaction that decided them

Analysis completion and notification delivery are two separate transactions, on purpose. When an analysis finishes, `AnalysisCompletionService` writes the result and a `PENDING` `OutboundNotification` row in the same transaction, so the notification can never be silently lost even if the process dies a moment later. A separate scheduled job (`NotificationPublisher`) sends it later, outside any transaction, since it's talking to an external webhook that can be slow or down. On failure it retries with real exponential backoff (`nextAttemptAt`, capped at `notification.max-attempts`) rather than immediately, and gives up to a terminal `FAILED` state — retryable by hand from the ops console — once it runs out of attempts.

### The loop closes at resolution

Resolving an incident isn't just a status flip. The on-call engineer fills in a structured write-up — root cause category and impact as choices, four free-text narrative fields — which gets assembled into postmortem content and saved through the exact same `PostmortemService.create` path a seeded postmortem goes through: embedded, stored, and immediately retrievable, with `sourceIncidentId` recording provenance. This is the actual mechanism behind "the system gets smarter over time." Nothing about the model changes. The corpus it retrieves from grows by one real, specific incident every time someone resolves one through the app.

## API

| Method | Path | Notes |
|---|---|---|
| POST | `/auth/register` | Create an account. Always `USER` role. |
| POST | `/auth/login` | Get a JWT. |
| GET | `/auth/me` | Current user. |
| POST | `/alerts` | Ingest one alert. Deduplicated on `(sourceSystem, externalAlertId)`. |
| GET | `/alerts/{id}` | One alert. |
| GET | `/alerts` | All alerts. |
| GET | `/incidents` | All incidents. |
| GET | `/incidents/{id}` | One incident. |
| GET | `/incidents/{id}/alerts` | Alerts correlated into an incident. |
| POST | `/incidents/{id}/resolve` | Resolve, with a structured postmortem body. Writes a real, retrievable postmortem as a side effect. |
| POST | `/incidents/{id}/analyze` | Submit analysis for an incident. |
| GET | `/analysis/{id}` | One analysis request. |
| GET | `/incidents/{id}/analysis` | All analyses for an incident, most recent first. |
| GET | `/postmortems` | All postmortems. |
| POST | `/postmortems` | Seed a postmortem directly, without an incident behind it. Admin only. |
| GET | `/incidents/{id}/notifications` | Notifications for an incident. |
| GET | `/admin/notifications` | Notifications, filterable by status. Admin only. |
| POST | `/admin/notifications/{id}/retry` | Retry a failed notification by hand. Admin only. |
| GET | `/admin/llm-usage` | The LLM usage log. Admin only. |
| GET | `/admin/circuit-breaker-status` | Live circuit breaker state and metrics. Admin only. |
| POST | `/admin/reset-demo-data` | Clears alerts, incidents, analyses, and notifications. Leaves users and postmortems alone, since those aren't demo noise. Admin only. |
| GET | `/demo/scenarios` | The seeded incident scenarios, for the picker. |
| POST | `/demo/simulate-alert-storm` | Fire a burst of alerts for one scenario. Admin only. |
| POST | `/demo/mock-webhook` | The default notification target. No auth — it's the receiving end of a webhook, not something a user calls directly. |

## Running it locally

Postgres, with pgvector:

```
docker compose up -d
```

Backend, from the project root (needs Java 17, and a `GEMINI_API_KEY` environment variable — Spring AI won't start without one):

```
mvnw spring-boot:run
```

An admin account (`admin@incident-copilot.local`, configurable via `app.admin.email` / `app.admin.password`) and the four scenario postmortems are seeded automatically on first startup.

Frontend, from `frontend/`:

```
npm install
npm run dev
```

Talks to `http://localhost:8080` by default; CORS is already configured for `http://localhost:5173` (`app.cors.allowed-origins`).

## Running the whole thing containerized

```
docker compose -f docker-compose.full.yml up --build
```

Builds the app image and runs it alongside Postgres, matching what actually deploys to Render — useful for confirming the Docker image works before pushing anything live.

## Tests

```
mvnw test
```

Every external dependency — the embedding model, the chat model, the notification sender — is faked (`FakeEmbeddingModel`, `FakeChatModel`, `FakeNotificationSender`), so the suite runs with zero real API calls and no cost. Integration tests run against a real Postgres via Testcontainers, not an in-memory substitute, so the pgvector-specific SQL actually gets exercised. Covers the correlation logic under real concurrency, the full lifecycle from alert to resolved postmortem end to end, the circuit breaker actually opening and closing under repeated failures, and the notification outbox surviving a mid-flight crash.

Frontend build is checked with `npm run build`.

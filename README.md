# Incident Copilot

An SRE incident-management backend, with a small React dashboard on top. Raw monitoring alerts get correlated into incidents, an LLM does root-cause analysis grounded in the company's own past postmortems, and every resolved incident's postmortem gets written back into that same knowledge base. So the next similar incident gets a specific answer, not a generic one.

The project exists to work through problems that are genuinely hard, not just CRUD with an LLM call bolted on:

1. Deciding, under real concurrency, whether an incoming alert belongs to an already-open incident or needs to start a new one. Two alerts landing at the same instant must not both decide "there's no existing incident" and each create their own.
2. Treating an external LLM as something that will fail sometimes, not an afterthought. It can time out, get rate-limited, or just be down, and the system has to fall back to something an on-call engineer can still use instead of blocking or crashing.
3. Making the RAG part of the "AI" checkable instead of trusted on faith. You should be able to see, per analysis, which historical postmortem (if any) the model was grounded in.
4. Delivering a notification about the outcome reliably, even though "analysis finished" and "notification sent" happen in different transactions, and the second one talks to an external system that can fail on its own.

## What it does

An ops engineer picks one of four seeded incident scenarios (a database connection pool exhaustion, a memory leak, an auth token rotation gone wrong, a payment gateway outage) and fires a burst of alerts for it. It's not one repeated line. Each scenario cycles through three differently worded symptoms, the way a real outage looks scrolling down a monitoring dashboard. Those alerts get embedded and correlated into one or more incidents in the background.

An on-call engineer opens the resulting incident, looks at the raw correlated alerts, and clicks Analyze. The backend embeds a summary of those alerts, runs a similarity search against every stored postmortem (both the seeded ones and ones written by resolving earlier incidents), and hands the closest matches to Gemini along with the alerts as context. What comes back is a likely root cause and concrete next steps. The UI also shows exactly which historical postmortem titles were pulled in for that specific analysis, so you can check the model isn't just making something up.

If Gemini is down, rate-limited, or times out, the analysis still finishes. It falls back to a result built directly from the raw correlated alerts, so the on-call engineer is never stuck staring at a spinner or an error page. Either way, a notification gets queued and delivered, and retried with backoff if delivery itself fails.

When the engineer resolves the incident, they fill in a short structured postmortem: root cause category, impact, what happened, root cause detail, resolution steps, follow-up actions. That write-up gets embedded and stored the exact same way a seeded postmortem does. It isn't just a record for later. It becomes context the system can pull from the next time a similar incident happens. That's the point of the whole project: the answers get more specific as more incidents get resolved through it, without retraining anything.

1. Ops engineer triggers a scenario, alerts fire.
2. Alerts get embedded and correlated into an incident.
3. On-call engineer analyzes the incident and sees a root cause plus retrieved context.
4. A notification gets queued and sent.
5. On-call engineer resolves the incident and writes a postmortem.
6. The postmortem gets embedded into the knowledge base, ready for the next analysis.

## Architecture

Backend: Java 17, Spring Boot 4 / Spring Framework 7, Spring Security with JWT, Spring Data JPA, PostgreSQL with the pgvector extension, Flyway for migrations, Spring AI for the Gemini integration (chat and embeddings), Resilience4j for circuit breaking and rate limiting, Testcontainers for integration tests against a real Postgres, Maven for the build.

Frontend: React with Vite and Tailwind v4. No router. There are exactly four views (login, incident list, incident detail, an ops console) and plain `useState` is enough to switch between them.

Deployment: a Dockerfile and a Render blueprint (`render.yaml`), plus a `docker-compose.full.yml` that builds the app image and runs it next to a real Postgres container, so the whole thing can be checked end to end before deploying anything for real.

Entities reference each other by plain UUID fields, not JPA relationships. An `Alert` has an `incidentId`, an `AnalysisRequest` has an `incidentId`, a `Postmortem` has an optional `sourceIncidentId`. Nothing is `@ManyToOne`. Foreign key constraints still exist at the database level, but the object graph in Java stays flat, and every table can be reasoned about on its own. That matters once alerts are landing concurrently from more than one source.

One exception to "just use JPA": vector embeddings never show up as a field on `Alert` or `Postmortem` at all. The database columns exist (`alerts.embedding`, `postmortems.embedding`, both `vector(768)`), but Hibernate never touches them. They're read and written entirely through dedicated `JdbcTemplate`-based repositories (`AlertEmbeddingRepository`, `PostmortemEmbeddingRepository`) using pgvector's own `?::vector` cast syntax. Letting Hibernate try to map a pgvector column directly has a long list of fragile edge cases. Keeping it out of the entity graph avoids all of them.

### Domain model

| Entity | Purpose |
|---|---|
| `User` | Login identity. Role is `USER` or `ADMIN`. `USER` is the on-call engineer (investigate, analyze, resolve, write postmortems). `ADMIN` is the ops console (trigger scenarios, watch system health). |
| `Alert` | One raw monitoring alert: source system, severity, title, raw payload. Embedded and correlated into an incident on ingest. |
| `Incident` | A correlated group of alerts. Tracks status (`OPEN` / `INVESTIGATING` / `RESOLVED`), correlated alert count, and, once resolved, the root cause summary written at resolution time. |
| `Postmortem` | A write-up, either seeded ahead of time or written when resolving an incident (`sourceIncidentId` records which). Embedded and retrievable by every future analysis. |
| `AnalysisRequest` | One request to analyze an incident. Tracks status (`PENDING → RUNNING → COMPLETED` / `DEGRADED` / `FAILED`), the result summary, and which postmortem titles were retrieved and used. |
| `LlmUsageLog` | One row per attempted LLM call, whatever the outcome (`SUCCESS`, `CIRCUIT_OPEN`, `RATE_LIMITED`, `TIMEOUT`, `ERROR`). This is what the ops console's usage log is reading. |
| `OutboundNotification` | One notification queued after an analysis completes. Delivered asynchronously outside the analysis transaction, retried with backoff on failure. |

## How the AI works

Two separate flows: correlating alerts as they come in, and analyzing an incident once it exists.

**Correlating an alert:**

1. A new alert arrives and gets embedded.
2. An advisory lock is taken, then the nearest open incident is found by embedding distance.
3. Close enough: attach to that incident. Not close enough: create a new one.

**Analyzing an incident:**

1. The incident's alerts get summarized and embedded.
2. The top-K most similar postmortems are retrieved.
3. A prompt is built from the alerts plus the retrieved postmortems.
4. The call to Gemini goes through a circuit breaker, a rate limiter, and a timeout.
5. If it succeeds: status `COMPLETED`, with a root cause and the retrieved postmortem titles recorded.
6. If it fails: status `DEGRADED`, falling back to a summary of the raw alerts.
7. Either way, a notification gets queued.

A few notes on why it's built this way:

- The advisory lock exists because an alert storm fires many alerts at once, and two alerts that should both start a new incident must not both check "does one exist yet," get "no," and each create their own.
- The circuit breaker and rate limiter are wired up as functional decorators (`CircuitBreaker.decorateSupplier`, `RateLimiter.decorateSupplier`), not the `@CircuitBreaker` / `@RateLimiter` annotations, because those annotations only work when the call comes through a Spring proxy. Calling the method from within the same class would silently skip the protection.
- `DEGRADED` exists so the on-call engineer never sees a spinner or an error page when Gemini is down. `FAILED` only happens if the incident's own data couldn't be loaded, before any LLM call was attempted.
- Resolving an incident writes a new postmortem through the same path a seeded one uses, which is what feeds the "future analyses" arrow in the diagram above.

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
| POST | `/admin/reset-demo-data` | Clears alerts, incidents, analyses, and notifications. Leaves users and postmortems alone since those aren't demo noise. Admin only. |
| GET | `/demo/scenarios` | The seeded incident scenarios, for the picker. |
| POST | `/demo/simulate-alert-storm` | Fire a burst of alerts for one scenario. Admin only. |
| POST | `/demo/mock-webhook` | The default notification target. No auth needed, since it's the receiving end of a webhook, not something a user calls directly. |

## Running it locally

Postgres, with pgvector:

```
docker compose up -d
```

Backend, from the project root (needs Java 17, and a `GEMINI_API_KEY` environment variable; Spring AI won't start without one):

```
mvnw spring-boot:run
```

An admin account (`admin@incident-copilot.local`, configurable via `app.admin.email` / `app.admin.password`) and the four scenario postmortems are seeded automatically on first startup.

Frontend, from `frontend/`:

```
npm install
npm run dev
```

Talks to `http://localhost:8080` by default. CORS is already configured for `http://localhost:5173` (`app.cors.allowed-origins`).

## Running the whole thing containerized

```
docker compose -f docker-compose.full.yml up --build
```

Builds the app image and runs it alongside Postgres, matching what deploys to Render. Useful for confirming the Docker image works before pushing anything live.

## Tests

```
mvnw test
```

Every external dependency (the embedding model, the chat model, the notification sender) is faked (`FakeEmbeddingModel`, `FakeChatModel`, `FakeNotificationSender`), so the suite runs with zero real API calls and no cost. Integration tests run against a real Postgres via Testcontainers, not an in-memory substitute, so the pgvector-specific SQL gets exercised too. Covers the correlation logic under real concurrency, the full lifecycle from alert to resolved postmortem end to end, the circuit breaker opening and closing under repeated failures, and the notification outbox surviving a mid-flight crash.

Frontend build is checked with `npm run build`.

# SmartQueue

Digital queue management for hospitals, banks, government offices, clinics and service centres. Customers (or guests, no account needed) join a queue from their phone, by browsing, scanning a QR code, and follow their token live. Staff call, serve, skip and recall tokens from a counter dashboard. Admins configure organizations, branches, queues, counters and staff, and read analytics.

> **Status honesty note.** The frontend type-checks, builds and passes its tests (19). The backend was written and statically reviewed but could **not be compiled or run** in the environment it was authored in (Maven Central was unreachable). Run `mvn verify` once (Docker required for the integration tests) before relying on it. See [Verification](#verification).

## Features

- **Join flows**: organization → branch → service, QR scan (camera or typed), or guest join with an opaque server-issued session.
- **Tokens**: unique per queue per day, configurable prefix (`A001`, priority `P001`), validated state machine `WAITING → CALLED → SERVING → COMPLETED`, plus `SKIPPED`, `CANCELLED`, `NO_SHOW`.
- **Staff**: call next, recall, start serving, complete, skip, no-show, cancel, requeue, move, pause/resume, counters.
- **Priority**: always-first, "after N normal tokens", or disabled, per queue.
- **ETA**: people ahead × average service time, blended with 14 days of real history and active counters. Always shown as an estimate with a confidence level, never as exact.
- **Real time**: STOMP over WebSocket; clients auto-reconnect, refetch on reconnect and poll while offline.
- **Admin**: dashboard, CRUD for all entities, analytics with charts (and table views), audit log, QR generate/view/download/print.
- **Security**: JWT access + rotating refresh tokens with reuse detection, BCrypt(12), lockout, rate limiting, CORS from env, security headers, no stack traces, object-level authorization (staff only operate assigned queues), audit logging.
- **Concurrency**: every mutating queue operation takes a per-queue `PESSIMISTIC_WRITE` row lock first; `@Version` is the second defence; DB unique constraint on `(queue_id, service_date, token_number)` is the last.

## Architecture

```
React + Vite + Tailwind  ──REST──▶  Spring Boot 3 (Java 21)  ──JPA/Flyway──▶  PostgreSQL 16
        ▲                                   │
        └──────── STOMP /ws (read-only) ◀───┘  (published after transaction commit)
```

Stack: Spring Web, Data JPA, Security, Validation, WebSocket, Flyway, JJWT, ZXing; React 18, TypeScript, react-router 6, Tailwind 3, recharts, @stomp/stompjs, jsQR, Vitest.

## Folder structure

```
smartqueue/
├── backend/
│   ├── pom.xml, Dockerfile
│   └── src/main/java/com/smartqueue/
│       ├── config/       AppProperties, SecurityConfig, DemoDataSeeder (dev only)
│       ├── controller/   Auth, Public, Token, Staff, Admin, Dashboard
│       ├── dto/          request/response records
│       ├── entity/       JPA entities + enums (TokenStatus state machine)
│       ├── exception/    ErrorCode, ApiException, GlobalExceptionHandler
│       ├── repository/   Spring Data repositories
│       ├── security/     JWT, filters, rate limiter, CurrentUser
│       ├── service/      QueueEngine (core), QueueOrdering, EstimationService, ...
│       └── websocket/    WebSocketConfig, RealtimePublisher
│   └── src/main/resources/db/migration/V1__init_schema.sql
│   └── src/test/         unit, concurrency and security integration tests
├── frontend/             src/{pages,components,hooks,services,layouts,types,test}
├── docker-compose.yml
└── .env.example
```

## Prerequisites

Java 21, Maven 3.9+, Node 20+, PostgreSQL 16 (or Docker). Docker is also needed for the backend integration tests (Testcontainers).

## Configuration

Copy `.env.example` to `.env`. Nothing secret is hardcoded.

| Variable | Required | Purpose |
|---|---|---|
| `JWT_SECRET` | yes | ≥ 32 chars. `openssl rand -base64 48` |
| `DB_URL` | no | default `jdbc:postgresql://localhost:5432/smartqueue` |
| `DB_USERNAME` / `DB_PASSWORD` | yes (password) | database credentials |
| `CORS_ALLOWED_ORIGINS` | prod | comma-separated frontend origins |
| `PUBLIC_BASE_URL` | prod | origin put in QR codes, e.g. `https://app.example.com` |
| `JWT_ACCESS_TTL_MINUTES` / `JWT_REFRESH_TTL_DAYS` | no | 15 / 7 |
| `LOGIN_MAX_ATTEMPTS` / `LOGIN_LOCKOUT_MINUTES` | no | 5 / 15 |
| `AUTH_RATE_LIMIT_PER_MINUTE` / `JOIN_RATE_LIMIT_PER_MINUTE` | no | per IP, 20 / 30 |
| `SPRING_PROFILES_ACTIVE` | no | `dev` enables demo seed data, never use in production |
| `DEMO_PASSWORD` | dev only | password for seeded demo accounts |
| `VITE_API_URL` (frontend) | prod | backend origin; empty in dev (Vite proxies `/api` and `/ws`) |
| `VITE_OPERATOR_NAME`, `VITE_CONTACT_EMAIL` (frontend) | prod | named on the Privacy and Terms pages; have your counsel review those pages |

## Run locally

```bash
# 1. database
docker run -d --name sq-db -e POSTGRES_DB=smartqueue -e POSTGRES_USER=smartqueue \
  -e POSTGRES_PASSWORD=change-me -p 5432:5432 postgres:16-alpine

# 2. backend (dev profile seeds the SmartCare Hospital demo)
cd backend
export DB_PASSWORD=change-me JWT_SECRET=$(openssl rand -base64 48) DEMO_PASSWORD='Choose-a-demo-pass-1!'
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run        # http://localhost:8080

# 3. frontend (new terminal)
cd frontend
npm install
npm run dev                                            # http://localhost:5173
```

Flyway creates the schema on first start.

### Demo data (development only)

With `SPRING_PROFILES_ACTIVE=dev` the seeder creates SmartCare Hospital → Mumbai Central → General Consultation, Billing, Pharmacy, each with Counters 1–3, and three **dev-only** accounts: `demo.admin`, `demo.staff`, `demo.customer`, all using the password you set in `DEMO_PASSWORD`. The seeder is disabled by default and must never run in production.

## Docker (everything)

```bash
cp .env.example .env     # fill DB_PASSWORD and JWT_SECRET
docker compose up --build
```

Starts PostgreSQL and the backend on :8080. Run the frontend with `npm run dev`, or deploy it separately (below).

## Tests

```bash
cd backend && mvn verify          # unit + Testcontainers integration tests (needs Docker)
cd backend && mvn test            # unit tests only; ITs are skipped without Docker
cd frontend && npm test           # Vitest
cd frontend && npx tsc -b && npm run build
```

Backend coverage: ordering/priority math, state machine, 25-way concurrent joins (distinct gap-free numbers), duplicate guest race, double-NEXT, 20 concurrent NEXT over 10 tokens, business rules (paused/full queue, priority modes, counter busy, cross-queue staff), and HTTP security (auth, lockout, role checks, guest flow, error shape, headers, QR PNG).

## Deployment

**Backend (Render / Railway / VPS)**: deploy `backend/` using its Dockerfile. Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS` (your frontend origin), `PUBLIC_BASE_URL` (frontend origin). Use managed PostgreSQL; on Render/Railway convert the provided URL to `jdbc:postgresql://host:port/db`. Health check: `/actuator/health`. Terminate TLS at the platform and keep WebSockets enabled. Do **not** set the `dev` profile.

**Frontend (Vercel / Netlify)**: root `frontend/`, build `npm run build`, output `dist`, env `VITE_API_URL=https://your-backend`. `vercel.json` already rewrites all routes to `index.html`.

**VPS**: `docker compose up -d` behind nginx/Caddy with HTTPS; forward `/ws` with upgrade headers.

Scale note: rate limiting is in-memory per instance. For several backend instances, put limits at the proxy or move the limiter to Redis; WebSocket broadcasting also uses the in-memory simple broker, so use one instance or swap in a broker relay.

## API overview

Errors are `{ code, message, fieldErrors?, timestamp }` with stable codes (`QUEUE_FULL`, `INVALID_TOKEN_STATE`, `DUPLICATE_ACTIVE_TOKEN`, ...).

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/login, register, refresh, logout`; `GET /api/auth/me` |
| Public | `GET /api/public/organizations`, `/organizations/{id}/branches`, `/branches/{id}/queues`, `/queues/{publicRef}`, `/queues/{publicRef}/qr` (PNG); `POST /api/public/tokens/{reference}/cancel` |
| Customer / guest | `POST /api/queues/{id}/join` (optional `X-Session-Id`), `GET /api/tokens/{reference}`, `GET /api/me/tokens` |
| Staff | `POST /api/queues/{id}/next, pause, resume`; `POST /api/tokens/{id}/recall, serving, complete, skip, no-show, cancel, requeue, move`; `GET /api/staff/queues`, `/queues/{id}/state`, `/queues/{id}/stats` |
| Admin | `/api/admin/` dashboard, analytics, organizations, branches, departments, services, queues (+`/config`), counters, staff, users, customers, tokens, audit-logs, settings |

## WebSocket

Endpoint `/ws` (STOMP). Subscribe to `/topic/queue/{publicRef}` (queue snapshot events) or `/topic/token/{reference}` (one token). Clients are read-only: `SEND` is rejected and subscriptions are restricted to those patterns. Events are published only after the DB transaction commits. The UI refetches on reconnect and polls while disconnected.

## Security notes

Guests receive an opaque session id; the unguessable token `reference` is the capability to view or cancel that token. QR codes contain only the public join URL. Admin-only data never goes to the browser without a role check, and staff are limited to assigned queues, re-checked against the database on each action.

## Verification

| Check | Status |
|---|---|
| Frontend type-check, production build, 19 tests | passed |
| Backend compile, unit and integration tests | **run `mvn verify` yourself** (not executed where this was authored) |
| End-to-end flow (join → call → complete, live updates, QR) | run locally with the steps above |

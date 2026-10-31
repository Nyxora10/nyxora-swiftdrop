# AI Agent Operating Guidelines for SwiftDrop (`nyxora-swiftdrop`)

> **Purpose:** This document defines the mandatory operating rules, behavioral expectations, and workflow protocols for any AI agent (Antigravity, Claude, Copilot, Gemini, etc.) contributing to the SwiftDrop project. Every AI interaction with this codebase must comply with these guidelines.

---

## 1. Golden Rules — Non-Negotiable

### 1.1 Ask First, Code Later
**Never rush to implement.** Before writing any code:
1. **Clarify** — If the user's request is ambiguous, has multiple valid interpretations, or touches architectural decisions, **stop and ask clarifying questions** before producing code.
2. **Propose** — For any non-trivial change (new endpoint, schema modification, new service wiring), present a brief plan or approach and get explicit approval before implementing.
3. **Confirm scope** — Ensure you understand which service, database, and layer the change belongs to. Never assume.

> ❌ **Bad:** User says "add tracking" → Agent immediately writes a full tracking feature.
> ✅ **Good:** User says "add tracking" → Agent asks: "Do you mean adding WebSocket push to the existing `/topic/parcels/{trackingNumber}` channel, or creating a new REST endpoint for polling? Which service should own this?"

### 1.2 Teach, Don't Just Build
**The user is learning.** This is a university group assignment. Every AI interaction is a teaching opportunity:
- **Explain the "why"** behind every design decision, pattern, or tool choice. Don't just say "use `@PreAuthorize`" — explain what it does, why it's the right fit here, and what alternatives exist.
- **Reference industry concepts** — When applying patterns (Strategy, Observer, State Machine), name them and briefly explain how they apply to SwiftDrop's context.
- **Link to the assignment requirements** — When implementing a feature, reference which assignment requirement it satisfies (e.g., "This covers the Integration requirement: message broker for status events").
- **Highlight trade-offs** — When there are multiple valid approaches, present the options with pros/cons so the user can make an informed decision.

### 1.3 Follow the Assignment Brief
This is a graded academic assignment. The authoritative requirements source is [`docs/Group Assignment.pdf`](./Group%20Assignment.pdf). Key grading areas:

| Component | Weight | What Matters |
| :--- | :--- | :--- |
| **Design Documentation** | 20 marks | Use case diagrams, ER diagrams, architecture proposal with justification, integration pattern design, sequence diagrams, design patterns note. |
| **Implementation** | 50 marks | Responsive frontend, secure REST APIs with validation and error handling, message broker integration, WebSocket real-time tracking, code quality, Docker Compose containerization. |
| **Testing** | 15 marks | Unit + integration tests, authorization rule tests, measurable code coverage with reports. |
| **Presentation / Demo** | 15 marks | Live demo of all core flows, architecture walkthrough, each member explains their contribution. |
| **Bonus: Kubernetes** | +5 marks | Full K8s deployment with ConfigMaps, Secrets, Ingress. |
| **Bonus: AI Assistant** | +5 marks | Generative AI chatbot calling backend APIs; MCP tools encouraged. |

**Every implementation decision must be traceable to these requirements.**

---

## 2. Architecture Boundaries — Strict Rules

### 2.1 Monorepo Service Map
Always respect the established service boundaries from [`docs/master-specification.md`](./master-specification.md):

| Service | Port | Database | Responsibility |
| :--- | :--- | :--- | :--- |
| `api-gateway` | 8080 | None (stateless) | Single entry point, JWT relay, RBAC pre-filter, rate limiting, CORS. |
| `auth-service` | 8081 | `auth_db` | User registration, login, JWTs, Google OAuth 2.0, merchant client credentials, account approvals. |
| `parcel-service` | 8082 | `parcel_db` | Parcel booking, pricing engine (Strategy), state machine lifecycle, STOMP WebSocket broker, MCP tools. |
| `notification-webhook-service` | 8083 | `notification_db` | RabbitMQ consumer, email/mock SMS dispatch, HMAC-signed merchant webhooks with exponential backoff retries. |
| `ai-assistant-service` | 8084 | — | Spring AI + MCP orchestrator, natural language query handler. |
| `frontend` | 3000 | — | Next.js (React, TypeScript, Tailwind CSS), customer/merchant/courier/admin portals, public tracker. |

### 2.2 Database Isolation — Zero Exceptions
Each service owns its database exclusively. **No cross-database queries, no shared tables, no foreign keys across databases:**
- `auth-service` → `auth_db` (tables: `roles`, `users`, `merchants`, `couriers`)
- `parcel-service` → `parcel_db` (tables: `delivery_zones`, `pricing_rules`, `parcels`, `parcel_status_history`)
- `notification-webhook-service` → `notification_db` (tables: `webhook_delivery_logs`)

**Cross-service data needs must be satisfied via:**
- REST API calls through the gateway (synchronous)
- RabbitMQ topic events (asynchronous)

### 2.3 Communication Patterns
- **Synchronous:** REST over HTTPS through `api-gateway` or internal service-to-service calls (Spring Cloud OpenFeign / RestClient).
- **Asynchronous:** RabbitMQ Topic Exchange `swiftdrop.events.topic` for domain events (`parcel.status.updated`, `parcel.created`, `user.registered`).
- **Real-Time Push:** STOMP over WebSockets via `notification-webhook-service` on `/topic/parcels/{trackingNumber}`.
- **Webhook Delivery:** HMAC-SHA256 signed POST requests with exponential backoff retries (10s → 30s → 2m → 10m → 30m, max 5 attempts) via Dead-Letter Exchange.

---

## 3. Code Quality Standards

### 3.1 Backend (Spring Boot 3 / Java 21)
- **Package structure:** `com.nyxora.swiftdrop.<service>/config|controller|dto|entity|exception|repository|service|mapper`
- **DTOs:** Use Java `record` types for all request/response objects. Annotate with Jakarta Bean Validation (`@NotBlank`, `@Valid`, `@Positive`, etc.).
- **Dependency Injection:** Constructor injection via Lombok `@RequiredArgsConstructor` only. Never use `@Autowired` on fields.
- **Primary Keys:** UUID (`@GeneratedValue(strategy = GenerationType.UUID)`) on all entities.
- **Auditing:** Include `created_at` and `updated_at` fields with JPA auditing on all entities.
- **Relationships:** Always `FetchType.LAZY` to prevent N+1 queries.
- **Error Handling:** Centralized `@RestControllerAdvice` returning RFC 7807 problem detail responses. Never leak stack traces or PII.
- **Security:** `@PreAuthorize` for method-level RBAC. Passwords hashed with BCrypt (strength 12). Environment variables for all secrets with `${VAR:default}` fallback syntax.

### 3.2 Frontend (Next.js / TypeScript)
- **App Router** (`app/` directory) with `strict: true` TypeScript.
- **No `any` types.** Define explicit interfaces for all API contracts.
- **Component hierarchy:** `components/ui/` (primitives) and `components/features/` (domain-specific).
- **Tailwind CSS** for styling. No inline styles.

### 3.3 Git Discipline
**Conventional Commits** format: `<type>(<scope>): <subject>`
- Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`
- Branches: `feature/<name>`, `bugfix/<name>`, `chore/<name>`

---

## 4. Workflow Protocol — How to Respond

### Step 1: Understand
- Read the user's request carefully.
- Identify which assignment requirement(s) it maps to.
- Check if it touches architecture boundaries or introduces new dependencies.

### Step 2: Clarify (if needed)
- Ask focused questions about scope, approach, or design trade-offs.
- Present options with brief pros/cons when multiple valid paths exist.

### Step 3: Explain
- Before writing code, briefly explain:
  - **What** you're about to do
  - **Why** this approach (which pattern, which principle)
  - **Where** in the monorepo this change lives
  - **Which** assignment requirement it addresses

### Step 4: Implement
- Write clean, production-quality code — no placeholder stubs, no `// TODO: implement later` in production paths.
- Include proper validation, error handling, and security annotations.
- Follow the package structure and naming conventions in Section 3.

### Step 5: Verify
- Confirm the code compiles and integrates correctly with existing services.
- Check that ports, database names, and exchange names are consistent with the master specification.
- Update documentation (`docs/`, `.env.example`, `README.md`) when adding new endpoints, services, or environment variables.

### Step 6: Teach
- After implementation, summarize what was built and **why it was built that way**.
- Point out relevant design patterns, security decisions, or integration choices.
- Suggest what the user might want to explore or implement next.

---

## 5. Things to Never Do

| ❌ Never | ✅ Instead |
| :--- | :--- |
| Hardcode passwords, JWT secrets, or API keys in code. | Use environment variables with fallback: `${JWT_SECRET:dev_secret}`. Update `.env.example`. |
| Create cross-database foreign keys or direct DB connections between services. | Use REST API calls or RabbitMQ events for cross-service data. |
| Use `@Autowired` on fields. | Use constructor injection with `@RequiredArgsConstructor`. |
| Use `any` type in TypeScript. | Define explicit interfaces and types. |
| Skip error handling or return raw stack traces. | Use `@RestControllerAdvice` with RFC 7807 responses. |
| Implement without explaining. | Always explain the "why" — this is a learning environment. |
| Rush large features without confirming scope. | Ask clarifying questions first. Present a plan and wait for approval. |
| Commit `.env` files or credentials. | Only commit `.env.example` with placeholder values. |
| Skip tests for new features. | Write at minimum unit tests; integration tests for event flows and auth rules. |
| Ignore the assignment marking rubric. | Trace every feature to the rubric (Design: 20, Implementation: 50, Testing: 15, Demo: 15). |

---

## 6. Reference Documents

| Document | Path | Purpose |
| :--- | :--- | :--- |
| **Assignment Brief** | [`docs/Group Assignment.pdf`](./Group%20Assignment.pdf) | Original graded assignment specification (20 marks, 10-member group). |
| **Master Specification** | [`docs/master-specification.md`](./master-specification.md) | Architecture, database schemas, integration patterns, design patterns, and security rules. |
| **Docker Compose** | [`docker-compose.yml`](../docker-compose.yml) | Local infrastructure orchestration (PostgreSQL 15, RabbitMQ 3.11). |
| **DB Init Script** | [`docker/postgres/init-multi-db.sql`](../docker/postgres/init-multi-db.sql) | Creates `auth_db`, `parcel_db`, `notification_db` with UUID extensions. |
| **Environment Template** | [`.env.example`](../.env.example) | Required environment variables for local development. |

---

## 7. Quick Decision Tree for AI Agents

```
User sends a request
│
├─ Is it ambiguous or touches architecture?
│  └─ YES → Ask clarifying questions. Do NOT implement yet.
│
├─ Is it a new feature or endpoint?
│  └─ YES → Propose a plan with:
│           - Which service owns it
│           - Which DB tables are affected
│           - Which assignment requirement it satisfies
│           - Wait for user approval before coding
│
├─ Is it a bugfix or small change?
│  └─ YES → Explain what's wrong and why, then fix it.
│
├─ Does it require a new environment variable?
│  └─ YES → Add to .env.example and document it.
│
└─ After implementation:
   └─ Explain what was built, which patterns were used,
      and what the user should understand from it.
```

# SwiftDrop Enterprise Platform (`nyxora-swiftdrop`)

[![CI Pipeline](https://github.com/Nyxora10/nyxora-swiftdrop/actions/workflows/ci.yml/badge.svg)](https://github.com/Nyxora10/nyxora-swiftdrop/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.13-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud-Gateway%204-green.svg)](https://spring.io/projects/spring-cloud-gateway)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)]()

An island-wide enterprise parcel delivery and courier management platform engineered for Sri Lanka's logistics landscape, built with cloud-native microservices architecture, perimeter API gateway security, multi-provider social authentication, and automated admin review workflows.

---

## 🏛️ System Architecture

SwiftDrop operates on a decoupled microservices architecture with a single perimeter gateway:

```
[ Client / Web / Mobile ]
            |
            v  Port 8080 (Reactive Netty)
┌────────────────────────────────────────────────────────┐
│                   API Gateway                          │
│  - Stateless JWT Authentication Filter                 │
│  - Role-Based Access Control (RBAC) Filter             │
│  - Upstream User Context Headers (X-User-Id, Role)     │
│  - Route Whitelist & Rate Limiting                     │
└──────────────┬─────────────────────────┬───────────────┘
               |                         |
               v Port 8081               v Port 8082 (Upcoming)
┌──────────────────────────────┐  ┌──────────────────────────────┐
│        Auth Service          │  │        Parcel Service        │
│  - Multi-Role Onboarding     │  │  - Parcel Booking & Pricing  │
│  - Local & Social Auth       │  │  - Dynamic Strategy Engine   │
│    (Google, Apple, Facebook) │  │  - Parcel State Machine      │
│  - Admin Approval Lifecycle  │  │  - Live Tracking API         │
│  - Rotating Refresh Tokens   │  └──────────────────────────────┘
│  - Swagger / OpenAPI 3 UI    │
└──────────────┬───────────────┘
               v
┌──────────────────────────────┐
│     PostgreSQL (auth_db)     │
│  - Users & Roles (RBAC)      │
│  - Merchants & Couriers      │
│  - Single-Use Refresh Tokens │
└──────────────────────────────┘
```

---

## 🚀 Key Implemented Features

### 1. Perimeter API Gateway (`services/api-gateway` - Port `8080`)
- **Stateless Perimeter Security:** Verifies incoming Bearer JWT tokens via `AuthenticationFilter` before routing to downstream internal services.
- **Role-Based Access Control (RBAC):** `RoleAuthorizationFilter` enforces role boundaries (`ROLE_CUSTOMER`, `ROLE_MERCHANT`, `ROLE_COURIER`, `ROLE_ADMIN`).
- **Context Propagation:** Injects verified claims (`X-User-Id`, `X-User-Email`, `X-User-Role`) as downstream HTTP headers.
- **Route Whitelist:** Exposes public endpoints (`/login`, `/register/**`, `/google`, `/apple`, `/facebook`, `/track/**`, Swagger UI) without token overhead.

### 2. Identity & Authentication Service (`services/auth-service` - Port `8081`)
- **Multi-Role Registration:**
  - **Customers:** Instant onboarding with active status and immediate JWT token pair.
  - **Merchants:** Business partner onboarding with registration number verification (`PENDING_APPROVAL`).
  - **Couriers:** Driver onboarding with license number and vehicle type verification (`PENDING_APPROVAL`).
- **Social OAuth 2.0 & OIDC Providers:**
  - 🔵 **Google OAuth 2.0:** Verified cryptographically against Google's public keys (`GoogleIdTokenVerifier`).
  - ⚫ **Apple Sign In:** Verified against Apple's live RSA JWKS keys (`https://appleid.apple.com/auth/keys`) with thread-safe caching; handles initial client-provided `fullName`.
  - 🔷 **Facebook Login:** Verified via Graph API `/v19.0/me` with `appsecret_proof` (HMAC-SHA256) preventing cross-app token spoofing; enforces verified email.
  - 🔄 **Unified Social Pipeline (DRY):** Shared 3-way user resolution (provider ID match -> verified email match with link preservation -> auto-registration) and lifecycle validation.
- **Session & Token Management:**
  - 15-minute stateless JWT access tokens (`HS256` via modern `jjwt 0.12.6`).
  - 7-day single-use rotating refresh tokens with token family invalidation upon reuse attack detection.
- **Admin Approval & Audit Lifecycle:**
  - Search and filter users by status (`PENDING_APPROVAL`, `ACTIVE`, `SUSPENDED`, `REJECTED`), role, and search keyword.
  - Partner inspection with merchant business details and courier vehicle/license data.
  - State machine transitions: **Approve** (`ACTIVE`), **Reject** (`REJECTED` with audit reason), **Suspend** (`SUSPENDED` with audit reason and immediate token revocation). Self-suspension is strictly blocked.
- **Interactive OpenAPI 3.0 / Swagger UI:**
  - Hosted at `http://localhost:8081/swagger-ui.html` and `http://localhost:8081/v3/api-docs`.
  - Configured with `BearerAuth` security scheme for interactive testing of secured endpoints.

---

## 🛠️ Technology Stack

| Layer | Technology | Details |
|---|---|---|
| **Language & Runtime** | Java 21 LTS | Eclipse Temurin 21 |
| **Framework** | Spring Boot 3.3.13 | Native Java 21 support |
| **Perimeter Routing** | Spring Cloud Gateway 4 | Reactive Netty, non-blocking I/O |
| **Security** | Spring Security 6 & JJWT 0.12.6 | Stateless JWT, Method Security (`@PreAuthorize`) |
| **Social Auth** | Google API Client, Apple JWKS, FB Graph API | Cryptographic signature & HMAC token validation |
| **Database & ORM** | PostgreSQL 15 & Spring Data JPA (Hibernate 6) | Relational schema with foreign keys and unique constraints |
| **API Documentation** | SpringDoc OpenAPI 2.6.0 | Swagger UI with Bearer JWT authorization |
| **Testing** | JUnit 5, Mockito, MockMvc, H2 Database | 89 hermetic tests with 100% test reliability |
| **CI/CD** | GitHub Actions | Automated build & test on `main` and `dev` branches |
| **Containerization** | Docker Compose | Local multi-database PostgreSQL 15 infrastructure |

---

## 📁 Repository Structure

```
nyxora-swiftdrop/
├── .github/
│   └── workflows/
│       └── ci.yml                 # GitHub Actions CI matrix workflow (JDK 21)
├── docker/
│   └── postgres/
│       └── init-multiple-dbs.sh   # Multi-DB PostgreSQL bootstrap script
├── docs/
│   ├── master-specification.md    # Comprehensive system specification & rubric
│   └── agent.md                   # AI Agent engineering protocol & constraints
├── services/
│   ├── api-gateway/               # Port 8080: Reactive perimeter routing & RBAC
│   │   ├── pom.xml
│   │   └── src/
│   └── auth-service/              # Port 8081: Identity, social logins & admin review
│       ├── pom.xml
│       └── src/
├── .env.example                   # Environment configuration template
├── docker-compose.yml             # Local infrastructure (PostgreSQL 15, auth_db)
└── README.md
```

---

## 🚦 Git Flow & Branching Strategy

The repository strictly adheres to standard team Git Flow:

- **`main`**: Production-ready, stable codebase. Direct commits are restricted.
- **`dev`**: Primary integration branch. All completed features are merged here first via Pull Requests.
- **`feature/*`**: Feature branches branched from `dev` and merged back into `dev` via Pull Requests with mandatory CI passing.
- **Release to Main**: Periodically, stable snapshots of `dev` are merged into `main` via PR.

---

## ⚡ Quick Start (Local Setup)

### Prerequisites
- **JDK 21** or higher
- **Apache Maven 3.9+**
- **Docker & Docker Compose**

### 1. Clone & Configure Environment
```bash
git clone https://github.com/Nyxora10/nyxora-swiftdrop.git
cd nyxora-swiftdrop

# Copy environment variable template
cp .env.example .env
```

### 2. Start Infrastructure
Start the multi-database PostgreSQL container:
```bash
docker compose up -d
```
*This boots PostgreSQL on `localhost:5432` and automatically initializes `auth_db`, `parcel_db`, and `notification_db`.*

### 3. Run Microservices
In separate terminal sessions:

**Start Auth Service (Port 8081):**
```bash
cd services/auth-service
mvn spring-boot:run
```

**Start API Gateway (Port 8080):**
```bash
cd services/api-gateway
mvn spring-boot:run
```

---

## 🧪 Testing & Quality Assurance

All services are covered by hermetic tests utilizing in-memory databases and mock verifiers:

```bash
# Run Auth Service test suite (69 tests)
mvn test -f services/auth-service/pom.xml

# Run API Gateway test suite (20 tests)
mvn test -f services/api-gateway/pom.xml
```

**Total Monorepo Tests:** **89 tests passing (100% success rate)**.

---

## 📖 API Documentation & Swagger UI

Once `auth-service` is running, open the interactive Swagger UI:
- **Swagger UI:** [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
- **OpenAPI 3 JSON Spec:** [http://localhost:8081/v3/api-docs](http://localhost:8081/v3/api-docs)

### Testing Authenticated Endpoints via Swagger:
1. Call `POST /api/v1/auth/login` (or any social endpoint like `/google`).
2. Copy the `accessToken` from the response body.
3. Click the green **Authorize** button at the top of Swagger UI.
4. Paste the access token into the `BearerAuth` input field and click **Authorize**.
5. All protected endpoints (`/api/v1/auth/me`, `/api/v1/auth/admin/**`, `/api/v1/auth/logout`) can now be tested directly in the browser!

---

## 🛡️ Key REST Endpoints Summary

| Method | Path | Auth / Role | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/register/customer` | Public | Register customer (immediate active status + JWT) |
| `POST` | `/api/v1/auth/register/merchant` | Public | Register merchant (`PENDING_APPROVAL`) |
| `POST` | `/api/v1/auth/register/courier` | Public | Register courier (`PENDING_APPROVAL`) |
| `POST` | `/api/v1/auth/login` | Public | Local email & password login |
| `POST` | `/api/v1/auth/google` | Public | Google OAuth 2.0 token exchange |
| `POST` | `/api/v1/auth/apple` | Public | Apple Sign In token exchange |
| `POST` | `/api/v1/auth/facebook` | Public | Facebook Login token exchange |
| `POST` | `/api/v1/auth/refresh` | Public | Rotate refresh token for new access token |
| `POST` | `/api/v1/auth/logout` | Authenticated | Revoke refresh tokens and terminate session |
| `GET`  | `/api/v1/auth/me` | Authenticated | Inspect authenticated user profile |
| `GET`  | `/api/v1/auth/admin/users` | `ROLE_ADMIN` | Paginated search and filter across users |
| `GET`  | `/api/v1/auth/admin/users/{userId}` | `ROLE_ADMIN` | Detailed profile with partner documents |
| `PUT`  | `/api/v1/auth/admin/users/{userId}/approve` | `ROLE_ADMIN` | Approve partner account to `ACTIVE` |
| `PUT`  | `/api/v1/auth/admin/users/{userId}/reject` | `ROLE_ADMIN` | Reject partner account with reason |
| `PUT`  | `/api/v1/auth/admin/users/{userId}/suspend` | `ROLE_ADMIN` | Suspend user account with reason |

---

## 👥 Authors & Team
- **Nyxora Development Team**

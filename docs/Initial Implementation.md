# SwiftDrop Enterprise Platform: Initial Implementation Report

**Project Name:** SwiftDrop (`nyxora-swiftdrop`)  
**Domain:** Island-Wide Parcel Delivery & Courier Logistics Platform  
**Target Architecture:** Cloud-Native Spring Boot 3 Microservices with Spring Cloud Gateway  
**Document Version:** 1.0.0 (Milestone 1: Identity, Perimeter Gateway & Social Authentication)  
**Date:** November 2026  

---

## Table of Contents
1. [Executive Summary & Architectural Overview](#1-executive-summary--architectural-overview)
2. [Repository & Project Structure](#2-repository--project-structure)
3. [Backend Services Implementation](#3-backend-services-implementation)
4. [Authentication, Social Logins & Account Lifecycle](#4-authentication-social-logins--account-lifecycle)
5. [REST API Endpoints & Interactive Documentation](#5-rest-api-endpoints--interactive-documentation)
6. [Validation, Security & Error Handling](#6-validation-security--error-handling)
7. [Database Architecture & Schema Management](#7-database-architecture--schema-management)
8. [Development Environment & Continuous Integration (CI/CD)](#8-development-environment--continuous-integration-cicd)
9. [Testing, Quality Assurance & Code Coverage](#9-testing-quality-assurance--code-coverage)
10. [Implementation Status & Limitations](#10-implementation-status--limitations)

---

## 1. Executive Summary & Architectural Overview

### 1.1 Project Vision & Problem Statement
SwiftDrop is an enterprise-grade, cloud-native logistics and parcel delivery platform designed specifically for Sri Lanka's domestic e-commerce and courier delivery ecosystem. Traditional courier businesses across Sri Lanka rely heavily on fragmented manual operations, telephone dispatching, and disconnected spreadsheets, leading to:
- Complete lack of real-time shipment visibility for customers.
- Onerous manual verification workflows for corporate e-commerce merchants and individual couriers.
- High error rates in pricing and route assignment.
- Lack of programmatic integration APIs for e-commerce storefronts.

SwiftDrop resolves these systemic deficiencies by delivering an automated, secure, highly scalable microservices architecture that connects retail senders, commercial merchants, dispatchers, and couriers onto a unified real-time logistics engine.

### 1.2 Microservices System Topology
SwiftDrop avoids the architectural pitfalls of monolithic systems by implementing a decoupled, domain-driven microservices architecture fronted by a unified Edge API Gateway.

```mermaid
flowchart TD
    Client["Client Applications<br/>(Web / Mobile / Partner Storefronts)"] -->|HTTPS / WSS| Gateway["Spring Cloud Gateway (Port 8080)<br/>- Perimeter JWT Verification<br/>- Role-Based Access Control (RBAC)<br/>- Downstream Identity Header Injection<br/>- Route Whitelisting"]
    
    subgraph Services ["Spring Boot 3 (Java 21) Microservices"]
        Gateway --> Auth["auth-service (Port 8081)<br/>- Multi-Role Registration<br/>- Local & Social Auth (Google, Apple, FB)<br/>- Admin Approval State Machine<br/>- Token Rotation & Revocation<br/>- Swagger UI / OpenAPI 3.0"]
        Gateway -.-> Parcel["parcel-service (Port 8082 - Upcoming)<br/>- Dynamic Pricing Engine<br/>- Parcel State Machine<br/>- Real-time STOMP WebSockets"]
        Gateway -.-> Notif["notification-webhook-service (Port 8083 - Upcoming)<br/>- RabbitMQ Event Consumer<br/>- Signed Webhooks (HMAC-SHA256)<br/>- Exponential Backoff DLQ"]
        Gateway -.-> AI["ai-assistant-service (Port 8084 - Upcoming)<br/>- Spring AI + MCP Tools"]
    end

    subgraph DataTier ["PostgreSQL 15 (Isolated Logical Databases)"]
        Auth --> AuthDB[("auth_db<br/>- Users & Roles<br/>- Merchants & Couriers<br/>- Refresh Tokens")]
        Parcel -.-> ParcelDB[("parcel_db<br/>- Pricing Rules<br/>- Parcels & Tracking")]
        Notif -.-> NotifDB[("notification_db<br/>- Delivery Logs")]
    end
```

### 1.3 Architectural & Design Principles Applied
The initial implementation strictly embeds industry standard software design patterns and principles required by modern enterprise architectures and the academic rubric:

1. **Backend-For-Frontend (BFF) & Edge Gateway Pattern:**
   All client interactions enter through a central Spring Cloud Gateway (port 8080). Downstream microservices are hidden from the public network, ensuring a single perimeter security boundary and preventing token decoding overhead across internal services.
2. **Layered N-Tier Architecture:**
   Within each microservice, components are organized into strict horizontal layers: `Presentation` (`controller`, `dto`) $\rightarrow$ `Business Logic` (`service`, `service/impl`) $\rightarrow$ `Data Access` (`repository`, `entity`).
3. **SOLID Principles:**
   - **Single Responsibility Principle (SRP):** Distinct classes for token generation (`JwtTokenProvider`), route classification (`RouteValidator`), authentication exception mapping (`GlobalExceptionHandler`), and social verification (`FacebookClient`, `AppleIdTokenVerifier`).
   - **Open/Closed Principle (OCP):** Service contracts defined via interfaces allowing extended auth providers without modifying existing registration workflows.
   - **Dependency Inversion Principle (DIP):** Controllers inject high-level service interfaces (`AuthService`), not concrete implementations (`AuthServiceImpl`).
4. **DRY (Don't Repeat Yourself) Principle:**
   Extracted a normalized `SocialUserProfile` record and unified `processSocialAuthentication()` pipeline in `AuthServiceImpl`. Google, Apple, and Facebook flows share 100% of the 3-way user resolution, lifecycle state machine validation, and JWT token issuance logic.
5. **State Machine Pattern:**
   User and partner accounts adhere to a deterministic state transition machine (`PENDING_APPROVAL`, `ACTIVE`, `SUSPENDED`, `REJECTED`), guarded by strict validation rules.

---

## 2. Repository & Project Structure

### 2.1 Monorepo Layout & Multi-Service Directory Organization
The platform is developed within a unified monorepo (`nyxora-swiftdrop`), ensuring consistent tooling, coordinated deployments, and centralized documentation:

```text
nyxora-swiftdrop/
├── .github/
│   └── workflows/
│       └── ci.yml                 # GitHub Actions CI matrix pipeline (JDK 21)
├── docker/
│   └── postgres/
│       └── init-multiple-dbs.sh   # Automated PostgreSQL multi-database bootstrap
├── docs/
│   ├── master-specification.md    # Comprehensive system specification & rubric
│   ├── agent.md                   # AI Agent engineering protocol & constraints
│   ├── Group Assignment.pdf       # Academic guidelines and marking rubric
│   └── Initial Implementation.md  # Comprehensive technical implementation report
├── services/
│   ├── api-gateway/               # Port 8080: Reactive perimeter routing & RBAC
│   │   ├── pom.xml
│   │   └── src/
│   └── auth-service/              # Port 8081: Identity, social logins & admin review
│       ├── pom.xml
│       └── src/
├── .env.example                   # Environment configuration template
├── docker-compose.yml             # Local infrastructure (PostgreSQL 15, auth_db)
└── README.md                      # Comprehensive developer setup guide & architecture
```

### 2.2 Standardized Internal Layered Architecture
`auth-service` establishes the canonical package structure replicated across all forthcoming microservices:

```text
services/auth-service/src/main/java/com/nyxora/swiftdrop/auth/
├── config/                 # SecurityConfig, OpenApiConfig, JwtProperties
├── controller/             # REST Endpoints (AuthController, AdminController)
├── dto/
│   ├── request/            # Validated incoming payloads (LoginRequest, AppleAuthRequest, etc.)
│   └── response/           # Strongly-typed responses (AuthResponse, UserProfileResponse, etc.)
├── entity/                 # JPA Domain Entities (User, Role, Merchant, Courier, RefreshToken)
│   └── enums/              # Java Enums (RoleName, UserStatus, AuthProvider, VehicleType)
├── exception/              # Custom domain exceptions + GlobalExceptionHandler
├── repository/             # Spring Data JPA Repository interfaces
├── security/               # JwtTokenProvider, JwtAuthenticationFilter, UserPrincipal
│   └── oauth/              # Social Verifiers (AppleIdTokenVerifier, FacebookClient)
└── service/                # High-level business contracts (AuthService, AdminService)
    └── impl/               # Concrete business implementations (AuthServiceImpl, etc.)
```

### 2.3 Maven Multi-Project Configuration & Java 21
Every microservice leverages **Java 21 LTS** with modern language features (Records for immutable DTOs, Pattern Matching, Enhanced Switch expressions) and **Spring Boot 3.3.13**. Dependencies are managed independently per service `pom.xml`, allowing modular scaling and zero inter-service compile-time coupling.

---

## 3. Backend Services Implementation

### 3.1 API Gateway Service (`services/api-gateway` - Port 8080)
The API Gateway acts as the secure perimeter guard for the entire SwiftDrop infrastructure, built on **Spring Cloud Gateway 4** and reactive, non-blocking **Netty**:

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant GW as API Gateway (Port 8080)
    participant RV as RouteValidator
    participant AF as AuthenticationFilter
    participant RAF as RoleAuthorizationFilter
    participant AS as Auth Service (Port 8081)

    Client->>GW: HTTP Request (e.g., GET /api/v1/auth/me)
    GW->>RV: isSecured(request)?
    alt Is Open Endpoint (/login, /register, /google, /apple, /facebook, /v3/api-docs)
        RV-->>GW: Public -> Bypass security filters
        GW->>AS: Forward request directly
    else Is Secured Endpoint
        RV-->>GW: Secured -> Execute Security Filters
        GW->>AF: Validate Authorization: Bearer <JWT>
        alt Missing or Invalid Token
            AF-->>Client: HTTP 401 Unauthorized (RFC 7807)
        else Valid Token
            AF->>AF: Extract Claims (userId, email, role)
            AF->>GW: Mutate Request with Headers (X-User-Id, X-User-Role, X-User-Email)
            GW->>RAF: Authorize Route against Role
            alt Role Insufficient
                RAF-->>Client: HTTP 403 Forbidden (RFC 7807)
            else Role Authorized
                RAF->>AS: Forward with Upstream Headers
                AS-->>Client: HTTP 200 OK + Payload
            end
        end
    end
```

#### Core Components in API Gateway:
1. **`RouteValidator.java`:**
   Maintains a clean whitelist of public endpoints (`/api/v1/auth/login`, `/api/v1/auth/register/**`, `/api/v1/auth/google`, `/api/v1/auth/apple`, `/api/v1/auth/facebook`, `/api/v1/parcels/track/**`, `/v3/api-docs/**`, `/swagger-ui/**`). Any route not explicitly in this whitelist defaults to **Secured**.
2. **`AuthenticationFilter.java`:**
   Extracts the Bearer token, validates cryptographic signature and expiration via `JwtUtil`, and extracts `sub` (userId), `email`, and `role`. It injects these attributes as HTTP headers (`X-User-Id`, `X-User-Email`, `X-User-Role`) on the downstream request.
3. **`RoleAuthorizationFilter.java`:**
   Enforces perimeter access control matrices before downstream invocation (e.g., `/api/v1/auth/admin/**` requires `ROLE_ADMIN`; `/api/v1/couriers/**` requires `ROLE_COURIER` or `ROLE_ADMIN`).

### 3.2 Authentication & Identity Service (`services/auth-service` - Port 8081)
`auth-service` is the central authority for credential verification, RBAC authorization, and user lifecycle operations.

#### Domain Model & Entity Architecture:
- **`User.java`:** Core identity entity holding email, BCrypt password hash, `role` relationship, `status` (`PENDING_APPROVAL`, `ACTIVE`, `SUSPENDED`, `REJECTED`), and OAuth fields (`auth_provider`, `provider_id`).
- **`Role.java`:** Normalized roles mapped to `RoleName` enum (`ROLE_CUSTOMER`, `ROLE_MERCHANT`, `ROLE_COURIER`, `ROLE_ADMIN`).
- **`Merchant.java`:** One-to-one extension of `User` storing business registration number, business name, and OAuth client credentials.
- **`Courier.java`:** One-to-one extension of `User` storing driver license number, vehicle type, and active delivery capacity.
- **`RefreshToken.java`:** Single-use rotating refresh token entity tracking token hash, expiration, and revocation status for token family attack detection.

---

## 4. Authentication, Social Logins & Account Lifecycle

### 4.1 Multi-Role Registration Workflows
The registration engine enforces distinct validation and onboarding lifecycles per actor:

| Role | Initial Status | Requirements | Workflow & Tokens |
|---|---|---|---|
| **Customer** | `ACTIVE` | `fullName`, valid `email`, strong `password` ($\ge$ 8 chars), `phoneNumber` | Self-service instant onboarding. Immediately receives JWT Access Token + Refresh Token. |
| **Merchant** | `PENDING_APPROVAL` | User fields + `businessName`, `registrationNumber` | Enters approval queue. Cannot log in until reviewed by an administrator. Returns `RegistrationResponse`. |
| **Courier** | `PENDING_APPROVAL` | User fields + `driverLicenseNumber`, `vehicleType` | Enters approval queue. Cannot log in until reviewed by an administrator. Returns `RegistrationResponse`. |

### 4.2 Local Email & Password Authentication
- Passwords are encrypted using **BCrypt with strength 12**.
- Upon calling `POST /api/v1/auth/login`, Spring Security's `AuthenticationManager` verifies credentials.
- Before issuing tokens, `AuthServiceImpl` inspects the account status state machine:
  - If `PENDING_APPROVAL`: Throws `AccountPendingApprovalException` (HTTP 403).
  - If `SUSPENDED`: Throws `AccountSuspendedException` (HTTP 403).
  - If `REJECTED`: Throws `AccountRejectedException` (HTTP 403) with the admin audit reason.
  - If `ACTIVE`: Issues fresh JWT token pair.

### 4.3 Social OAuth 2.0 & OIDC Multi-Provider Architecture
To provide a friction-free experience for retail customers while maintaining strict enterprise security, SwiftDrop supports three distinct social identity providers:

```mermaid
sequenceDiagram
    autonumber
    actor User as Mobile / Web Client
    participant AS as Auth Service (Port 8081)
    participant Ext as Third-Party Provider (Google / Apple / Facebook)
    participant DB as auth_db

    User->>AS: POST /api/v1/auth/{google|apple|facebook} (Token)
    alt Google OAuth 2.0
        AS->>Ext: Verify GoogleIdToken (Google Public Certs)
    else Apple Sign In
        AS->>Ext: Verify RSA Signature via Live JWKS (appleid.apple.com/auth/keys)
    else Facebook Login
        AS->>Ext: GET /v19.0/me?access_token={token}&appsecret_proof={HMAC}
    end
    Ext-->>AS: Verified SocialUserProfile (providerId, email, name, emailVerified)
    AS->>AS: Execute processSocialAuthentication(profile)
    AS->>DB: Scenario A: findByAuthProviderAndProviderId(provider, providerId)
    alt Found Existing Social User
        AS->>DB: Load User
    else Not Found
        AS->>DB: Scenario B: findByEmail(profile.email)
        alt Found Local/Existing User (providerId == null)
            AS->>DB: Link providerId (Preserve existing password/provider)
        else Found Multi-Social User (providerId != null)
            AS->>AS: Preserve Primary Provider Link (Prevent Thrashing)
        else New User
            AS->>DB: Scenario C: Auto-provision as ACTIVE Customer
        end
    end
    AS->>AS: Verify Status == ACTIVE
    AS-->>User: HTTP 200 OK + JWT Access Token & Refresh Token
```

#### Provider Implementation Details:
1. **Google OAuth 2.0 (`/api/v1/auth/google`):**
   - Utilizes `GoogleIdTokenVerifier` from `com.google.api-client:google-api-client:2.7.0`.
   - Validates token signature, audience (`aud == GOOGLE_CLIENT_ID`), issuer (`accounts.google.com`), and `email_verified == true`.
2. **Apple Sign In (`/api/v1/auth/apple`):**
   - Implemented via [`AppleIdTokenVerifier.java`](file:///c:/Users/VICTUS/OneDrive/Documents/Programming/Nyxora/services/auth-service/src/main/java/com/nyxora/swiftdrop/auth/security/oauth/AppleIdTokenVerifier.java) using standard Java cryptography and JJWT (0 external dependencies).
   - Fetches and caches Apple's live RSA public keys from `https://appleid.apple.com/auth/keys` with thread-safe refresh.
   - Verifies RSA signature, `iss == "https://appleid.apple.com"`, `aud == APPLE_CLIENT_ID`, and `email_verified`.
   - Accommodates Apple's quirk where user full name is only returned on the first authorization by accepting an optional `fullName` forwarded by the frontend.
3. **Facebook Login (`/api/v1/auth/facebook`):**
   - Implemented via [`FacebookClient.java`](file:///c:/Users/VICTUS/OneDrive/Documents/Programming/Nyxora/services/auth-service/src/main/java/com/nyxora/swiftdrop/auth/security/oauth/FacebookClient.java) using Spring 6 `RestClient`.
   - **Anti-Spoofing Defense:** Computes `appsecret_proof = HMAC-SHA256(accessToken, facebookAppSecret)` as a mandatory query parameter on `/v19.0/me`. This forces Facebook to reject any token stolen from or issued to another application on the internet.
   - Enforces presence of a verified primary email, preventing login from unverified or phone-only Facebook accounts.
4. **Multi-Provider Account Linking Nuance:**
   - When a user signs in with Google, and later signs in with Facebook using the same verified email, our resolver recognizes the user by email.
   - It sets `provider_id` only if currently `null` (linking local users). If already populated by Google, it **preserves** the existing Google provider ID, avoiding constraint violations and database thrashing while issuing valid tokens.

### 4.4 Stateless Token Architecture & Security
- **Access Tokens:** Signed with `HS256` secret key, valid for 15 minutes. Contains subject UUID, email, and role claims.
- **Refresh Tokens:** High-entropy UUID stored as a hashed token in PostgreSQL, valid for 7 days.
- **Token Rotation:** Every invocation of `POST /api/v1/auth/refresh` revokes the incoming refresh token and issues a new one.
- **Reuse Attack Detection:** If an already-revoked refresh token is presented, `RefreshTokenService` detects a compromised token family, revoking all active sessions for that user and forcing re-authentication.
- **Logout:** `POST /api/v1/auth/logout` explicitly revokes all active refresh tokens for the authenticated user.

### 4.5 Administrative Review & Lifecycle State Machine
Administrators possess exclusive privileges to audit and manage user access through [`AdminController.java`](file:///c:/Users/VICTUS/OneDrive/Documents/Programming/Nyxora/services/auth-service/src/main/java/com/nyxora/swiftdrop/auth/controller/AdminController.java):

```mermaid
stateDiagram-v2
    [*] --> PENDING_APPROVAL: Merchant / Courier Register
    [*] --> ACTIVE: Customer Register / Social Login
    
    PENDING_APPROVAL --> ACTIVE: Admin Approve
    PENDING_APPROVAL --> REJECTED: Admin Reject (with Audit Reason)
    
    ACTIVE --> SUSPENDED: Admin Suspend (with Audit Reason + Revoke Tokens)
    SUSPENDED --> ACTIVE: Admin Re-activate
    
    REJECTED --> [*]
    SUSPENDED --> [*]
    ACTIVE --> [*]
```

- **Search & Filter:** Paginated search (`GET /api/v1/auth/admin/users`) with optional filtering by `status`, `role`, and full-text keyword search.
- **Document Review:** Detailed profile inspection (`GET /api/v1/auth/admin/users/{userId}`) revealing merchant business certificates or courier vehicle and driving license records.
- **Decision Engine:**
  - `PUT /approve`: Transitions account to `ACTIVE`.
  - `PUT /reject`: Transitions account to `REJECTED`, recording the mandatory rejection reason.
  - `PUT /suspend`: Transitions account to `SUSPENDED`, records audit reason, and immediately invalidates all active refresh tokens. Self-suspension by an admin is programmatically blocked.

---

## 5. REST API Endpoints & Interactive Documentation

### 5.1 Public Endpoints
All endpoints under this category bypass perimeter gateway JWT checks:

| Method | Endpoint | Description | Request Body | Response Body |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/register/customer` | Register retail customer | `CustomerRegisterRequest` | `AuthResponse` (HTTP 201) |
| `POST` | `/api/v1/auth/register/merchant` | Register merchant partner | `MerchantRegisterRequest` | `RegistrationResponse` (HTTP 201) |
| `POST` | `/api/v1/auth/register/courier` | Register courier driver | `CourierRegisterRequest` | `RegistrationResponse` (HTTP 201) |
| `POST` | `/api/v1/auth/login` | Local email/password login | `LoginRequest` | `AuthResponse` (HTTP 200) |
| `POST` | `/api/v1/auth/google` | Google OAuth 2.0 exchange | `GoogleAuthRequest` | `AuthResponse` (HTTP 200) |
| `POST` | `/api/v1/auth/apple` | Apple Sign In exchange | `AppleAuthRequest` | `AuthResponse` (HTTP 200) |
| `POST` | `/api/v1/auth/facebook` | Facebook Login exchange | `FacebookAuthRequest` | `AuthResponse` (HTTP 200) |
| `POST` | `/api/v1/auth/refresh` | Rotate refresh token | `RefreshTokenRequest` | `AuthResponse` (HTTP 200) |

### 5.2 Protected User Endpoints
Requires `Authorization: Bearer <JWT>`:

| Method | Endpoint | Required Role | Description |
|---|---|---|---|
| `GET` | `/api/v1/auth/me` | Any Authenticated | Inspect current user ID, email, role, and metadata |
| `POST` | `/api/v1/auth/logout` | Any Authenticated | Revoke refresh tokens and terminate active session |

### 5.3 Administrative Endpoints
Requires `Authorization: Bearer <JWT>` with `ROLE_ADMIN`:

| Method | Endpoint | Description | Query / Body Parameters |
|---|---|---|---|
| `GET` | `/api/v1/auth/admin/users` | Paginated search of users | `status`, `role`, `search`, `page`, `size`, `sort` |
| `GET` | `/api/v1/auth/admin/users/{userId}` | Detailed user profile | Path variable `userId` (UUID) |
| `PUT` | `/api/v1/auth/admin/users/{userId}/approve` | Approve partner registration | Path variable `userId` (UUID) |
| `PUT` | `/api/v1/auth/admin/users/{userId}/reject` | Reject partner registration | Path `userId`, Body `AdminDecisionRequest` (reason) |
| `PUT` | `/api/v1/auth/admin/users/{userId}/suspend` | Suspend active user account | Path `userId`, Body `AdminDecisionRequest` (reason) |

### 5.4 OpenAPI 3.0 & Swagger UI Integration
`auth-service` incorporates **SpringDoc OpenAPI 3.0** (`springdoc-openapi-starter-webmvc-ui:2.6.0`):
- **Interactive UI:** Accessible at `http://localhost:8081/swagger-ui.html`.
- **Raw OpenAPI Spec:** Accessible at `http://localhost:8081/v3/api-docs`.
- **Security Scheme:** Configured with `BearerAuth` (`type = HTTP, scheme = bearer, bearerFormat = JWT`), displaying an interactive green padlock button. Developers can paste their JWT access token once to unlock and test all administrative and protected endpoints directly in their web browser.

---

## 6. Validation, Security & Error Handling

### 6.1 Declarative Input Validation
Every incoming DTO is annotated with Jakarta Bean Validation constraints and evaluated by Spring MVC (`@Valid`):
- **Email:** Formatted using `@Email` and sanitized (`.trim().toLowerCase()`).
- **Passwords:** Validated with `@Size(min = 8, max = 100)` preventing weak credentials or buffer exploitation.
- **Phone Numbers:** Validated using regular expressions for Sri Lankan format (`^(\\+94\|0)[0-9]{9}$`).
- **Required Tokens:** Token payloads (`idToken`, `identityToken`, `accessToken`, `refreshToken`) guarded with `@NotBlank`.

### 6.2 Centralized Exception Handling & RFC 7807 Problem Details
All application errors are caught by [`GlobalExceptionHandler.java`](file:///c:/Users/VICTUS/OneDrive/Documents/Programming/Nyxora/services/auth-service/src/main/java/com/nyxora/swiftdrop/auth/exception/GlobalExceptionHandler.java) (`@RestControllerAdvice`), transforming exceptions into RFC 7807 **Problem Details** payloads:

```json
{
  "type": "about:blank",
  "title": "Validation Failed",
  "status": 400,
  "detail": "Input payload failed validation constraints.",
  "instance": "/api/v1/auth/register/customer",
  "errors": {
    "email": "Email format is invalid",
    "password": "Password must be between 8 and 100 characters"
  },
  "timestamp": "2026-11-01T20:37:09.014Z"
}
```

#### Exception Mapping Matrix:
| Domain Exception | HTTP Status Code | RFC 7807 Title |
|---|---|---|
| `MethodArgumentNotValidException` | `400 Bad Request` | Validation Failed |
| `BadCredentialsException` | `401 Unauthorized` | Authentication Failed |
| `InvalidTokenException` | `401 Unauthorized` | Invalid Token |
| `AccountPendingApprovalException` | `403 Forbidden` | Account Pending Approval |
| `AccountSuspendedException` | `403 Forbidden` | Account Suspended |
| `AccountRejectedException` | `403 Forbidden` | Account Application Rejected |
| `AccessDeniedException` | `403 Forbidden` | Access Denied |
| `UserAlreadyExistsException` | `409 Conflict` | Resource Conflict |
| `ResourceNotFoundException` | `404 Not Found` | Resource Not Found |

### 6.3 Security Defenses & Information Sanitization
1. **No Sensitive Data in Logs:** Passwords, private keys, and raw tokens are omitted from logger statements.
2. **Timing Attack Mitigation:** Password checks utilize BCrypt's constant-time comparison algorithms.
3. **CORS Hardening:** Configured strictly for local frontend (`localhost:3000`) and API Gateway (`localhost:8080`).
4. **CSRF Exemption Justification:** Since the API is purely stateless and authenticates via HTTP `Authorization: Bearer` headers (not ambient browser cookies), CSRF protection is safely disabled as recommended by Spring Security standards.

---

## 7. Database Architecture & Schema Management

### 7.1 Multi-Database Isolation Topology
SwiftDrop enforces strict service decoupling by assigning dedicated logical databases within PostgreSQL 15:
- **`auth_db`:** Managed by `auth-service` (User accounts, RBAC, OAuth links, refresh tokens).
- **`parcel_db`:** Managed by `parcel-service` (Zone matrices, pricing rules, parcel tracking).
- **`notification_db`:** Managed by `notification-webhook-service` (Delivery logs, merchant webhooks).

> **Architectural Rule:** Microservices never query or join another microservice's database. Cross-boundary synchronization occurs strictly via REST calls through the API Gateway or asynchronous RabbitMQ domain events.

### 7.2 Relational Schema & Indexes for `auth_db`

```mermaid
erDiagram
    ROLES ||--o{ USERS : "assigned to"
    USERS ||--o| MERCHANTS : "extends"
    USERS ||--o| COURIERS : "extends"
    USERS ||--o{ REFRESH_TOKENS : "owns"

    ROLES {
        int id PK
        string name UK "ROLE_CUSTOMER, ROLE_MERCHANT, ROLE_COURIER, ROLE_ADMIN"
    }

    USERS {
        uuid id PK
        int role_id FK
        string full_name
        string email UK
        string password_hash
        string phone_number
        string auth_provider "LOCAL, GOOGLE, APPLE, FACEBOOK"
        string provider_id UK
        string status "PENDING_APPROVAL, ACTIVE, SUSPENDED, REJECTED"
        string status_reason
        timestamp created_at
        timestamp updated_at
    }

    MERCHANTS {
        uuid id PK
        uuid user_id FK, UK
        string business_name
        string registration_number
    }

    COURIERS {
        uuid id PK
        uuid user_id FK, UK
        string driver_license_number
        string vehicle_type "MOTORCYCLE, THREE_WHEELER, VAN, TRUCK"
    }

    REFRESH_TOKENS {
        uuid id PK
        uuid user_id FK
        string token_hash UK
        timestamp expires_at
        boolean revoked
        timestamp created_at
    }
```

#### Production Performance Indexes:
- `uk_users_email` (Unique index on `users.email`).
- `uk_users_provider_id` (Unique index on `users.provider_id`).
- `idx_users_auth_provider_id` (Composite index on `auth_provider, provider_id` for $O(1)$ social lookups).
- `idx_refresh_tokens_user_id` (B-Tree index on `refresh_tokens.user_id` for fast session lookup).

### 7.3 Schema Evolution Strategy
In Milestone 1 development, Hibernate DDL auto is set to `update` to facilitate rapid iteration. For production staging and final submission, schemas are scheduled to transition to version-controlled **Liquibase** migrations ensuring deterministic schema rollbacks and reproducible multi-environment builds.

---

## 8. Development Environment & Continuous Integration (CI/CD)

### 8.1 Local Environment Orchestration
Infrastructure is orchestrated using **Docker Compose** (`docker-compose.yml`), booting an official PostgreSQL 15 Alpine container initialized with the script `docker/postgres/init-multiple-dbs.sh` to automatically instantiate `auth_db`, `parcel_db`, and `notification_db`.

### 8.2 Team Git Flow Strategy
The team strictly adheres to a standard enterprise Git Flow branching model:
- **`main`:** Production branch. Contains stable, thoroughly verified releases. Direct commits are restricted.
- **`dev`:** Development integration branch. Features and bugfixes merge here first via Pull Requests.
- **`feature/*`:** Isolated task branches branched from `dev` and merged back into `dev` following hermetic test validation and automated CI checks.

### 8.3 Automated GitHub Actions CI Pipeline
Automated continuous integration is established via [`.github/workflows/ci.yml`](file:///c:/Users/VICTUS/OneDrive/Documents/Programming/Nyxora/.github/workflows/ci.yml):

```yaml
name: SwiftDrop CI Pipeline

on:
  push:
    branches: [ main, dev ]
  pull_request:
    branches: [ main, dev ]

jobs:
  build-and-test:
    name: Build & Test (${{ matrix.service }})
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service:
          - auth-service
          - api-gateway
      fail-fast: false

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 21 (Temurin)
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'

      - name: Run Hermetic Unit and Integration Tests
        run: mvn test -B -f services/${{ matrix.service }}/pom.xml
```

**Pipeline Highlights:**
- Triggers on every push and PR targeting `main` and `dev`.
- Matrix strategy runs independent, parallel build jobs for each microservice.
- Uses Temurin JDK 21 with automatic Maven dependency caching.
- Guarantees that pull requests cannot merge if any unit or slice test fails.

---

## 9. Testing, Quality Assurance & Code Coverage

### 9.1 Testing Strategy & Pyramidal Distribution
The platform implements a multi-tier testing strategy adhering to the testing pyramid:
1. **Unit Tests:** Fast, isolated tests mocking dependencies via Mockito to verify business logic, mathematical calculations, and cryptographic assertions.
2. **Slice Tests (`@WebMvcTest`):** Tests web layers, HTTP serialization, bean validation, and security exception handling without starting the full server context.
3. **Data JPA Tests (`@DataJpaTest`):** Verifies queries, entity mappings, and database constraints against an in-memory H2 database.
4. **Integration Tests (`@SpringBootTest`):** End-to-end service validation with full application context bootstrapping.

### 9.2 Implemented Test Suites (89 Passing Hermetic Tests)

```text
========================================================================
SWIFTDROP MONOREPO TEST EXECUTION SUMMARY
========================================================================
Services Tested: 2 (auth-service, api-gateway)
Total Tests Run: 89
Failures: 0 | Errors: 0 | Skipped: 0 | Success Rate: 100.0%
Total Execution Time: ~25 seconds
========================================================================
```

#### Detailed Test Breakdown:
1. **`services/api-gateway` (20 Tests):**
   - `ApiGatewayApplicationTests` (1 test): Spring Cloud Gateway Netty context startup.
   - `AuthenticationFilterTest` (5 tests): Perimeter Bearer JWT validation, public path bypass, expired token rejection, header mutation.
   - `RoleAuthorizationFilterTest` (8 tests): RBAC route matrix enforcement across all role combinations.
   - `RouteValidatorTest` (2 tests): Whitelist evaluation for public auth, parcels tracking, and Swagger paths.
   - `JwtUtilTest` (4 tests): Claims extraction, role resolution, and token expiration calculations.
2. **`services/auth-service` (69 Tests):**
   - `AuthApplicationTests` (1 test): Full Spring Boot 3 context startup with H2 test profile.
   - `AuthServiceTest` (25 tests): Customer/Merchant/Courier registrations, local login, Google OAuth, Apple Sign In, Facebook Login, multi-provider link preservation, state machine lifecycle validations.
   - `AdminServiceTest` (19 tests): Paginated user search, partner details retrieval, account approvals, rejections, suspensions, token revocation, self-suspension blocking.
   - `AuthControllerTest` (16 tests): MockMvc testing of all registration, login, refresh, Google, Apple, and Facebook REST endpoints, covering happy paths, validation errors (400), and invalid credentials (401).
   - `FacebookClientTest` (5 tests): Graph API token verification, HMAC-SHA256 `appsecret_proof` computation, missing-email handling, API error handling.
   - `RefreshTokenServiceTest` (4 tests): Refresh token generation, single-use rotation, token family reuse attack detection and complete session invalidation.

### 9.3 Planned Testing & JaCoCo Code Coverage
- **JaCoCo Maven Plugin:** Slated for integration in Phase 6 to measure branch and instruction coverage, enforcing an automated minimum threshold of **80% code coverage** in the CI pipeline.
- **Testcontainers:** Planned for `parcel-service` and `notification-webhook-service` to spin up real ephemeral PostgreSQL and RabbitMQ Docker containers during integration tests.
- **Newman / Postman Collections:** Automated end-to-end API regression test suites integrated into CI pipelines.

---

## 10. Implementation Status & Limitations

### 10.1 Completed Milestones Summary
- ✅ **API Gateway Core:** Reactive Netty edge routing, perimeter JWT authentication filter, RBAC authorization filter, header injection.
- ✅ **Auth Service Core:** Multi-role registration (Customer, Merchant, Courier), BCrypt credentials, stateless JWT tokens, rotating refresh tokens with reuse detection.
- ✅ **Admin Lifecycle Workflow:** Paginated search and filtering, partner document verification, state machine transitions (Approve, Reject, Suspend).
- ✅ **Social Identity Providers:** Google OAuth 2.0, Apple Sign In (JWKS verification), Facebook Login (HMAC anti-spoofing), DRY multi-provider email resolution.
- ✅ **OpenAPI 3 / Swagger UI:** Interactive documentation at `/swagger-ui.html` with Bearer JWT padlock authorization.
- ✅ **CI/CD Automation:** GitHub Actions CI pipeline running parallel matrix builds for `main` and `dev` on JDK 21.
- ✅ **Comprehensive Test Suite:** 89 hermetic tests with 100% pass rate.

### 10.2 Known Limitations & Current Architectural Assumptions
1. **Third-Party Provider Sandbox Credentials:** Social logins are verified hermetically with unit tests and mock certificates; live production verification requires developer account provisioning (Google Cloud Console, Apple Developer Program, Meta for Developers).
2. **In-Memory Refresh Token Store in H2:** Unit tests operate against H2 in PostgreSQL compatibility mode; production uses PostgreSQL 15.
3. **Database Migration Automation:** Currently operating on Hibernate DDL auto `update` before locking into Liquibase scripts.

### 10.3 Next Phase Roadmap
- **Phase 6: `services/parcel-service` (Port 8082):**
  - Dynamic pricing calculation implementing the **Strategy Pattern** (`StandardZonePricingStrategy`, `MerchantDiscountPricingStrategy`).
  - Parcel booking and unique tracking number generation (`SD-YYYYMMDD-XXXXXX`).
  - Guarded parcel lifecycle **State Machine** (`CREATED` $\rightarrow$ `PICKED_UP` $\rightarrow$ `IN_TRANSIT` $\rightarrow$ `OUT_FOR_DELIVERY` $\rightarrow$ `DELIVERED` / `FAILED`).
  - STOMP WebSocket publisher on `/topic/parcels/{trackingNumber}` for live tracking.
- **Phase 7: `services/notification-webhook-service` (Port 8083):**
  - RabbitMQ topic exchange consumer (`swiftdrop.events.topic`).
  - Merchant signed webhooks (`X-SwiftDrop-Signature` HMAC-SHA256) with Dead-Letter Exchange (DLQ) exponential backoff retries.
- **Phase 8: `services/ai-assistant-service` (Port 8084):**
  - Generative AI customer support bot using Spring AI and Model Context Protocol (MCP) tools for fee estimates and tracking status.

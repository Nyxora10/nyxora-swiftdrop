# SwiftDrop Documentation Hub

Welcome to the **SwiftDrop (`nyxora-swiftdrop`)** project documentation directory. This repository contains the master architectural decisions, database schemas, integration flows, and coding guidelines for the platform.

---

## 📖 Primary Documentation

- [**Master Specification & Architecture Guide**](./master-specification.md)
  The authoritative technical reference for the 10-member engineering team, AI pair programmers, and technical stakeholders. It covers:
  - **Technology Stack**: Next.js, Spring Boot 3, Java 21, PostgreSQL, RabbitMQ, Spring Cloud Gateway, Spring AI + MCP.
  - **Monorepo Directory Structure**: Directory breakdown and component mapping.
  - **Use Case Catalogue (UC1 - UC22)**: Complete functional requirements across Public Users, Customers, Merchants, Couriers, and Admins.
  - **Multi-Database ER Design**: PostgreSQL isolation strategy across `auth_db`, `parcel_db`, and `notification_db`.
  - **Integration & Messaging Architecture**: RabbitMQ Topic Exchanges, Dead-Letter Exchanges (DLX), HMAC-SHA256 merchant webhooks, and STOMP WebSockets.
  - **Software Design Patterns & Security**: Strategy pattern for dynamic pricing, guarded parcel lifecycle state machine, stateless JWT authentication, and RBAC rules.

---

## 🎯 Quick Links for Development & AI Agents

| Resource | Path | Description |
| :--- | :--- | :--- |
| **Master Specification** | [`docs/master-specification.md`](./master-specification.md) | Full architectural blueprint and entity specifications. |
| **Docker Compose** | [`docker-compose.yml`](../docker-compose.yml) | Local multi-service infrastructure (PostgreSQL, RabbitMQ). |
| **Multi-DB Initialization** | [`docker/postgres/init-multi-db.sql`](../docker/postgres/init-multi-db.sql) | SQL script creating `auth_db`, `parcel_db`, and `notification_db`. |
| **Environment Config Template** | [`.env.example`](../.env.example) | Template for local and containerized environment variables. |
| **Services Directory** | [`services/`](../services/) | Monorepo root for all Spring Boot microservices. |
| **Frontend Directory** | [`frontend/`](../frontend/) | Next.js client and portal applications. |

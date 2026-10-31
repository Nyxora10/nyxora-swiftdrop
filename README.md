# SwiftDrop Enterprise Platform (`nyxora-swiftdrop`)

An island-wide enterprise parcel delivery and courier management platform built using a cloud-native microservices architecture.

## Tech Stack
* **Frontend:** Next.js (TypeScript, Tailwind CSS)[cite: 1]
* **Backend:** Spring Boot 3 (Java 21) Microservices, Spring Cloud Gateway[cite: 1]
* **Database:** PostgreSQL (Multi-database topology: `auth_db`, `parcel_db`, `notification_db`)[cite: 1]
* **Messaging & Real-Time:** RabbitMQ (Topic Exchange), STOMP WebSockets[cite: 1]
* **DevOps:** Docker Compose, Kubernetes, NGINX Ingress[cite: 1]

## Quick Start (Local Development)
1. Copy the environment variables:
   ```bash
   cp .env.example .env
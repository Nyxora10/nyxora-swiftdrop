# SwiftDrop Enterprise Platform (`nyxora-swiftdrop`)

An island-wide enterprise parcel delivery and courier management platform built using a cloud-native microservices architecture.

## Tech Stack
* **Frontend:** Next.js (TypeScript, Tailwind CSS)
* **Backend:** Spring Boot 3 (Java 21) Microservices, Spring Cloud Gateway
* **Database:** PostgreSQL (Multi-database topology: `auth_db`, `parcel_db`, `notification_db`)
* **Messaging & Real-Time:** RabbitMQ (Topic Exchange), STOMP WebSockets
* **DevOps:** Docker Compose, Kubernetes, NGINX Ingress

## Quick Start (Local Development)
1. Copy the environment variables:
   ```bash
   cp .env.example .env
   ```
2. Start the infrastructure services (PostgreSQL & RabbitMQ):
   ```bash
   docker compose up -d
   ```

## Documentation
For complete technical specifications, architecture diagrams, database schemas, and integration flows, refer to:
* 📄 [**SwiftDrop Master Specification & Architecture Guide**](docs/master-specification.md)
* 📁 [**Documentation Hub**](docs/README.md)
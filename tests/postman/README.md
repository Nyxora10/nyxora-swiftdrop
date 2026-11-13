# SwiftDrop API regression suite (Task 8.4)

`swiftdrop-collection.json` is a Postman collection (v2.1) that tests the core APIs **through the API gateway**,
using the demo accounts from `docker/postgres/seed-data.sql`. It runs automatically in CI
(job **API Regression Tests (Postman/Newman)** in `.github/workflows/ci.yml`).

| Folder | Checks |
|---|---|
| 00 Health | Gateway health endpoint |
| 01 Login with seed accounts | admin, customer, both couriers, merchant; saves their tokens |
| 02 Authentication security | wrong password 401, validation 400, missing/invalid token 401 |
| 03 Registration and session lifecycle | /me, register, duplicate email 409, refresh, logout, revoked refresh token 401 |
| 04 Admin user management (RBAC) | admin list/search/details, customer and courier get 403 |
| 05 Courier profile and shift | courier profile, go on duty, validation 400, customer gets 403 |
| 06 Dispatch (parcel-service) | admin unassigned queue, customer 403, no token 401 |
| 07 Webhook delivery logs | no token 401, customer 403 |

Requests run **in order**: the logins in folder 01 save tokens that later folders use.

## Run locally (Windows PowerShell)
Start the stack with a fresh, seeded database:
```powershell
docker compose down -v
docker compose up -d --build postgres rabbitmq auth-service parcel-service notification-webhook-service api-gateway
```
Wait about a minute, then run Newman, either with Node.js:
```powershell
npx --yes newman@6.2.1 run tests/postman/swiftdrop-collection.json --env-var baseUrl=http://localhost:8080
```
or with Docker (no Node.js needed):
```powershell
docker run --rm -v "${PWD}/tests/postman:/etc/newman" postman/newman:6-alpine run swiftdrop-collection.json --env-var baseUrl=http://host.docker.internal:8080
```
You can also import the file into the Postman app (Import -> File) and use "Run collection".

## Adding tests
When a feature is merged (booking, pricing, tracking, merchant OAuth, courier execution...), add its requests to a new
folder in Postman, export the collection (v2.1) over this file, and run it locally before opening the PR.

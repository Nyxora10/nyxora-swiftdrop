# SwiftDrop on Kubernetes (Task 7.3)

Local cluster: **Docker Desktop Kubernetes** (kubeadm mode, so it can use the images you built with `docker build`).

## Files
| File | What |
|---|---|
| `namespace.yaml` | Namespace `swiftdrop` |
| `configmaps.yaml` | Shared, non-secret settings (service URLs, DB/RabbitMQ hosts, storage) |
| `secrets.yaml` | Public **dev** defaults only (same as `.env.example`). Real secrets are created with `kubectl create secret` |
| `postgres.yaml`, `rabbitmq.yaml` | StatefulSets + Services with persistent volumes |
| `<service>.yaml` | Deployment + Service per microservice, with startup/readiness/liveness probes |
| `frontend.yaml` | Next.js Deployment + Service |
| `ingress.yaml` | NGINX Ingress on `http://localhost` (`/api`, `/ws-swiftdrop` -> gateway, `/` -> frontend) |
| `hpa.yaml` | CPU autoscalers (1–3 replicas, 70% CPU) for the 5 backend services |

## One-time cluster add-ons
```powershell
# NGINX Ingress Controller
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.12.0/deploy/static/provider/cloud/deploy.yaml
# metrics-server (needed by the HPAs) + Docker Desktop TLS fix
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.7.2/components.yaml
kubectl patch deployment metrics-server -n kube-system --type=json --patch-file k8s/addons/metrics-server-insecure-tls.json
```

## Deploy
```powershell
# Images (backend images come from `docker compose build`; the frontend needs the Ingress URL baked in)
docker compose build
docker build -t swiftdrop/frontend:k8s --build-arg NEXT_PUBLIC_API_URL=http://localhost --build-arg NEXT_PUBLIC_WEBSOCKET_URL=http://localhost/ws-swiftdrop ./frontend

kubectl apply -f k8s/namespace.yaml
kubectl create configmap postgres-init -n swiftdrop --from-file=docker/postgres/init-multi-db.sql --from-file=docker/postgres/seed-data.sql
kubectl create secret generic swiftdrop-gemini -n swiftdrop --from-literal=GEMINI_API_KEY=<your key>   # optional
kubectl apply -f k8s/

kubectl get pods,svc,ingress,hpa -n swiftdrop
```
Open http://localhost. Backend pods may restart once or twice while Postgres/RabbitMQ start; that is expected.

## Remove
```powershell
kubectl delete namespace swiftdrop   # deletes everything, including the database volumes
```

## Notes
- auth, parcel and notification have no Spring Boot Actuator yet, so their probes check the TCP port. Switch to `httpGet /actuator/health` once Actuator is added.
- ai-assistant-service needs the `swiftdrop-gemini` secret; without it the pod crash-loops (same as docker-compose).

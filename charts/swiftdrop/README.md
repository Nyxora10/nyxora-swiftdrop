# SwiftDrop Helm chart (Task 7.4)

Packages the whole stack (5 microservices, frontend, PostgreSQL, RabbitMQ, Ingress, HPAs) as one release.
Same resources as the plain manifests in `k8s/`, but configurable per environment.

| File | Use |
|---|---|
| `values.yaml` | Defaults for every setting |
| `values-dev.yaml` | Local laptop: 1 replica, HPA 1–2, chart creates the dev secrets |
| `values-prod.yaml` | 2 replicas, HPA 2–5, more memory, bigger volumes, secrets created outside the chart |

## Install (from the repo root)
```powershell
helm upgrade --install swiftdrop charts/swiftdrop -n swiftdrop --create-namespace `
  -f charts/swiftdrop/values-dev.yaml `
  --set-file postgres.initSql=docker/postgres/init-multi-db.sql
kubectl get pods,svc,ingress,hpa -n swiftdrop
```
`--set-file` passes the real `docker/postgres/init-multi-db.sql`, so the script is not copied into the chart.

Needs the ingress-nginx controller and metrics-server (see `k8s/README.md`) and the images
`swiftdrop/<service>:local` and `swiftdrop/frontend:k8s`.

Optional AI key (never in values or Git):
```powershell
kubectl create secret generic swiftdrop-gemini -n swiftdrop --from-literal=GEMINI_API_KEY=<key>
```

## Other commands
```powershell
helm lint charts/swiftdrop -f charts/swiftdrop/values-dev.yaml --set-file postgres.initSql=docker/postgres/init-multi-db.sql
helm template swiftdrop charts/swiftdrop -f charts/swiftdrop/values-prod.yaml --set-file postgres.initSql=docker/postgres/init-multi-db.sql
helm list -n swiftdrop
helm uninstall swiftdrop -n swiftdrop
```

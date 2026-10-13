# infra

Configuration d'exécution locale (Phase 1) :

- `docker-compose.yml` : PostgreSQL, backend, frontend
- stack d'observabilité : Grafana LGTM (OpenTelemetry), Langfuse (Phase 3)

Les Dockerfiles et manifestes ajoutés ici sont analysés par Trivy (misconfig) et hadolint en CI.

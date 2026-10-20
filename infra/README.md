# infra

Configuration d'exécution locale.

| Fichier | Rôle |
|---|---|
| `docker-compose.yml` | PostgreSQL 18 (loopback uniquement, volume `postgres-data`) |
| `.env.example` | Variables à copier dans `.env` (ignoré par Git) : mot de passe PostgreSQL, utilisateur InvestAI (`INVESTAI_USERNAME`, `INVESTAI_PASSWORD_HASH` produit par `node scripts/hash-password.mjs`), clé EODHD facultative, empreinte du jeton MCP facultative (`INVESTAI_MCP_TOKEN_HASH`, `node scripts/mcp-token.mjs`) |

Démarrage : voir [docs/setup.md](../docs/setup.md#lancer-lapplication-en-local).

À venir : stack d'observabilité Grafana LGTM / OpenTelemetry des appels d'outils MCP (Phase 3.0b),
images backend/frontend (Phase 9). Les Dockerfiles et manifestes ajoutés ici sont analysés par Trivy en CI.

# Architecture Decision Records

| # | Décision | Statut |
|---|---|---|
| [0001](0001-stack-technique.md) | Stack technique : Spring Boot 4 / Java 25 / Angular 22 en monorepo | Accepté |
| [0002](0002-framework-ia-langchain4j.md) | Framework IA : LangChain4j | Remplacé par 0010 |
| [0003](0003-garde-fous-qualite.md) | Garde-fous qualité et sécurité dès la Phase 0 | Accepté |
| [0004](0004-api-contract-first.md) | API REST documentée par OpenAPI, client Angular généré | Accepté |
| [0005](0005-persistance-postgres-flyway.md) | Persistance : PostgreSQL 18, Flyway, Testcontainers | Accepté |
| [0006](0006-contrat-erreurs-et-logs.md) | Contrat d'erreur (RFC 9457 + `code`) et logs JSON corrélés | Accepté |
| [0007](0007-authentification-mono-utilisateur.md) | Authentification mono-utilisateur : session, CSRF, identifiants hors du code | Accepté |
| [0008](0008-bibliotheque-ui-angular-material.md) | Bibliothèque UI : Angular Material | Accepté |
| [0009](0009-fournisseur-donnees-marche.md) | Données de marché : fichiers différés Euronext (MiFIR) + EODHD gratuit | Accepté |
| [0010](0010-ia-via-mcp-uniquement.md) | IA via un serveur MCP uniquement (Claude Desktop / Claude Code, sans appel à l'API) | Proposé |

Créer un nouvel ADR : commande `/new-adr <titre>` (modèle : [template.md](template.md)).

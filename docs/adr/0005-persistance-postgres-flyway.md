# 0005 — Persistance : PostgreSQL, Flyway, Testcontainers

- **Statut** : Accepté
- **Date** : 2026-10-13

## Contexte

Les données (portefeuilles, transactions, simulations, conversations de l'agent, news) sont
relationnelles et financières : intégrité, transactions et types décimaux exacts sont requis. Le
schéma doit évoluer de façon traçable, et les tests doivent tourner sur le même moteur que la prod.

## Options étudiées

### Gestion du schéma
- **Flyway** — migrations SQL versionnées, simples à relire. ➖ pas de rollback automatique.
- **Liquibase** — formats XML/YAML, rollback. ➖ plus verbeux, moins pédagogique pour apprendre le SQL.
- **`ddl-auto=update`** (Hibernate) — ➖ non déterministe, dangereux sur des données réelles.

### Base de test
- **Testcontainers** (vrai PostgreSQL) — ➕ fidèle à la prod. ➖ nécessite Docker.
- **H2 en mémoire** — ➖ dialecte différent, faux positifs.

## Décision

- **PostgreSQL 18** (image `postgres:18.x-alpine`), lancé localement par `infra/docker-compose.yml`
  et démarré automatiquement par Spring Boot (`spring-boot-docker-compose`, profil `local`).
- **Flyway** possède le schéma (`db/migration/V<n>__*.sql`) ; Hibernate est en
  `ddl-auto=validate` ; `open-in-view=false`.
- **Testcontainers** + `@ServiceConnection` pour tous les tests d'intégration (`@IntegrationTest`),
  même image que le compose (vérifié par `ComposeImageConsistencyTest`).
- Entités JPA et repositories Spring Data confinés à `infrastructure` (ArchUnit).

## Conséquences

- ➕ Mêmes versions en local, en test et en CI ; schéma relu en code review.
- ➖ Docker obligatoire pour lancer l'app et les tests d'intégration.
- ➖ Une migration mergée ne se modifie plus : toute correction passe par une nouvelle migration.

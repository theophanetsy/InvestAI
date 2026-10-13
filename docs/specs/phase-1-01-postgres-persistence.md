# Phase 1.1 — PostgreSQL, Docker Compose et socle de persistance

## Contexte

Toutes les features (portefeuilles, simulations, mémoire de l'agent, news) ont besoin d'une base
relationnelle. Cette étape met en place la base et ses outils, **sans aucune table métier**.

## Critères d'acceptation

1. **Given** un poste avec Docker et `infra/.env` rempli, **when** je lance
   `docker compose -f infra/docker-compose.yml up -d`, **then** un PostgreSQL 18 démarre, n'écoute
   que sur `127.0.0.1` et passe son healthcheck.
2. **Given** le profil `local`, **when** je lance `./mvnw spring-boot:run`, **then** Spring Boot
   démarre automatiquement le compose (intégration `spring-boot-docker-compose`) et se connecte à
   la base sans configuration supplémentaire.
3. **Given** une base vide, **when** l'application démarre, **then** Flyway applique les migrations
   de `db/migration` et Hibernate **valide** le schéma (`ddl-auto=validate`, jamais de génération).
4. **Given** les tests d'intégration (`*IT`), **when** `./mvnw verify` s'exécute (local ou CI),
   **then** une base PostgreSQL éphémère est fournie par Testcontainers, avec la même version
   majeure que le compose.
5. **Given** le code, **then** ArchUnit garantit que les entités JPA (`@Entity`) et les repositories
   Spring Data ne vivent que dans `..infrastructure..`, et que `api`/`application` n'utilisent pas
   `jakarta.persistence`.
6. Aucun secret commité : le mot de passe vient de `infra/.env` (ignoré par Git), un
   `infra/.env.example` documente les variables ; le compose échoue explicitement si la variable
   manque.

## Hors périmètre

- Tables métier (Phase 2), ShedLock (Phase 2), Spring Security (Phase 1.3).
- Conteneurisation du backend/frontend dans le compose (avec l'observabilité, Phase 1.6).

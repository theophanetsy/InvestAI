# 0001 — Stack technique

- **Statut** : Accepté
- **Date** : 2026-10-13

## Contexte

Projet perso d'apprentissage : monter en compétence sur Java/Spring Boot et Angular, et sur
l'intégration d'IA dans une application web. Un seul utilisateur, exécution locale d'abord, cloud ensuite.

## Options étudiées

- **Spring Boot + Angular** (compétences visées) — écosystème mature, typage fort des deux côtés.
- Python (FastAPI) + React — écosystème IA plus riche, mais hors objectif d'apprentissage.

## Décision

- Backend : **Java 25 (LTS)**, **Spring Boot 4.1**, Maven wrapper, PostgreSQL (Phase 1).
- Frontend : **Angular 22** (standalone, signals, zoneless), TypeScript 6 strict, Vitest, Playwright.
- **Monorepo** (`backend/`, `frontend/`, `infra/`, `docs/`) : une PR peut couvrir API + UI,
  CI filtrée par chemin.
- Architecture backend hexagonale légère par module métier, vérifiée par ArchUnit.

## Conséquences

- ➕ Typage strict de bout en bout, outillage qualité très mature côté Java.
- ➖ Spring Boot 4 est récent : certaines libs (LangChain4j starters) peuvent avoir un temps de
  retard → vérifier la compatibilité à chaque phase.
- Suivi : réévaluer les versions à chaque montée majeure (Dependabot).

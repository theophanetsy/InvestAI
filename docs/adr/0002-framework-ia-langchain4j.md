# 0002 — Framework IA : LangChain4j

- **Statut** : Accepté
- **Date** : 2026-10-13

## Contexte

L'app intègre un agent conversationnel avec tools, mémoire, MCP (client et serveur), un agent
planifié (news) et de l'observabilité LLM. Le choix des modèles/fournisseurs est reporté à la Phase 3.

## Options étudiées

- **LangChain4j** — AI Services déclaratifs, tools, mémoire, RAG, guardrails, client MCP,
  nombreux fournisseurs ; starters Spring Boot.
- **Spring AI** — intégration Spring native, observabilité Micrometer intégrée, client/serveur MCP.

## Décision

LangChain4j (choix de l'utilisateur), derrière des ports du domaine `assistant` / `news` pour
garder le domaine indépendant (règle ArchUnit : pas de `dev.langchain4j` dans `domain`).

## Conséquences

- ➕ API agentique riche, indépendance vis-à-vis du fournisseur de modèle.
- ➖ Compatibilité des starters avec Spring Boot 4 à vérifier en Phase 3 ; observabilité à
  brancher (listeners LangChain4j → OpenTelemetry / Langfuse).
- Suivi : si le starter Spring Boot 4 n'est pas disponible, utiliser LangChain4j « core » avec une
  configuration Spring manuelle.

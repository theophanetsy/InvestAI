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

## Mise à jour — 2026-10-13 : versions beta acceptées

InvestAI est un POC personnel : les modules LangChain4j encore en beta (dont l'intégration
Spring Boot) sont acceptés, avec trois garde-fous :

- **Version exacte épinglée** dans le `pom.xml` (pas de plage), montées de version via Dependabot
  et validées par la CI.
- **Confinement** : seuls les packages `..infrastructure..` importent `dev.langchain4j`
  (règle ArchUnit à ajouter en Phase 3.0, sur le modèle de la règle JPA). Le domaine et la couche
  `application` ne voient que des ports : changer de framework ne touche que les adaptateurs.
- Une API beta qui casse lors d'une montée de version est corrigée dans l'adaptateur, jamais en
  contournant un contrôle qualité.

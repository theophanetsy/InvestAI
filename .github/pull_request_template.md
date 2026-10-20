## Quoi / Pourquoi

<!-- Lien vers l'issue ou la spec (docs/specs/...) et résumé du changement. -->

Closes #

## Comment tester

<!-- Étapes de vérification manuelle, si pertinent. -->

## Definition of Done

- [ ] Tests unitaires et d'intégration ajoutés/mis à jour (test-first)
- [ ] `cd backend && ./mvnw verify` vert (si backend touché)
- [ ] `cd frontend && npm run check && npm run e2e` vert (si frontend touché)
- [ ] Aucune règle de lint/analyse désactivée, aucun seuil abaissé
- [ ] Pas de secret, de donnée personnelle ou de clé d'API dans le diff
- [ ] Contrat OpenAPI et client généré à jour (si API modifiée)
- [ ] ADR ajouté dans `docs/adr/` (si décision structurante)
- [ ] `CLAUDE.md` mis à jour si une nouvelle convention apparaît
- [ ] Outils MCP / consignes de conseil : tests et jeux de cas mis à jour (à partir de la Phase 3)

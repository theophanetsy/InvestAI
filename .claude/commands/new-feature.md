---
description: Implémente une feature de bout en bout (spec → tests → code → vérification → PR)
argument-hint: <numéro d'issue ou description courte>
---

Implémente la feature suivante en respectant strictement le workflow de `CLAUDE.md` :

**Feature :** $ARGUMENTS

1. **Spec** : si c'est un numéro d'issue, lis-la (`gh issue view`). Sinon rédige une spec courte
   dans `docs/specs/<slug>.md` (contexte, critères d'acceptation Given/When/Then, hors périmètre)
   et fais-la valider avant de continuer.
2. **Plan** : liste les fichiers à créer/modifier par couche (`domain`, `application`,
   `infrastructure`, `api`, frontend `features/...`). Signale toute décision qui mérite un ADR.
3. **Branche** : `git switch -c feat/<module>-<slug>` depuis `main` à jour.
4. **Test-first** : pour chaque critère d'acceptation, écris d'abord le test (unitaire domaine,
   puis intégration/API, puis composant/E2E) et vérifie qu'il échoue pour la bonne raison.
5. **Implémentation** minimale pour faire passer les tests, puis refactoring.
6. **Vérification** : `/check`. Tout doit être vert sans désactiver de règle.
7. **Commit(s)** Conventional Commits avec scope du module, puis propose d'ouvrir la PR
   (`gh pr create`) avec le template rempli. Ne pousse pas sans accord.

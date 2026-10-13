---
description: Crée un Architecture Decision Record dans docs/adr/
argument-hint: <titre de la décision>
---

Crée un nouvel ADR pour la décision : **$ARGUMENTS**

1. Trouve le prochain numéro libre dans `docs/adr/` (format `NNNN-titre-en-kebab-case.md`).
2. Utilise le modèle `docs/adr/template.md`. Statut initial : `Proposé`.
3. Remplis : contexte (contraintes, forces en présence), options étudiées (au moins 2, avec
   avantages/inconvénients), décision, conséquences (positives, négatives, suivi).
4. Si des informations manquent (budget, préférence), pose la question plutôt que d'inventer.
5. Ajoute le lien dans `docs/adr/README.md`.

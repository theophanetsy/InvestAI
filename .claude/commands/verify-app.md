---
description: Vérifie un changement dans la vraie application (stack locale isolée + navigateur)
argument-hint: <critères à vérifier, chemin de spec ou description du changement>
---

Délègue à l'agent `app-verifier` la vérification suivante dans l'application réelle :

**À vérifier :** $ARGUMENTS

1. Si rien n'est précisé : déduis le périmètre du diff (`git diff --stat main...HEAD`) et de la
   spec de la phase en cours (`docs/specs/`), et transmets ses critères d'acceptation.
2. Lance l'agent `app-verifier` avec ces critères ; il démarre et arrête lui-même la stack
   (`npm run stack:start` / `npm run stack:stop`, Docker requis).
3. Résume son rapport : verdict, anomalies avec reproduction. Une anomalie confirmée = un test de
   non-régression (unitaire ou e2e) avant de la corriger.

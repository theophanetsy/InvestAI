# Phase 2.0 — Choix du fournisseur de données de marché (ADR + spike)

## Contexte

Les données de marché sont la dépendance critique de la suite : dashboard (valorisation, courbe
d'évolution), simulations datées, backtests et tools de l'agent. Un mauvais choix (couverture
Euronext incomplète, historique court, conditions d'utilisation interdisant le cache) se paierait
cher une fois le port `MarketDataProvider` et le cache en base construits (Phase 2, étape
suivante). Cette étape **décide** sur des données réelles plutôt que sur la documentation
commerciale ; elle ne produit **aucun code de production**.

Usage cible : une seule personne, un PEA et un compte-titres, quelques dizaines de lignes au plus,
cours rafraîchis quelques fois par jour (pas de temps réel), historique quotidien profond.
Benchmarks via des ETF cotés (CAC 40, MSCI World), pas via des données d'indices, souvent sous
licence.

Contraintes du propriétaire :

- **Budget : 0 €.** Seules les offres gratuites sont candidates ; un tarif payant est noté pour
  information (porte de sortie), il ne départage pas.
- **Univers d'un PEA chez un courtier en ligne**, pas seulement les grandes valeurs : toutes les
  actions d'Euronext Paris (CAC 40, SBF 120, petites valeurs, Euronext Growth), les actions
  européennes éligibles PEA cotées sur d'autres places (Amsterdam, Bruxelles, Francfort,
  Milan…) et les ETF éligibles PEA. On doit pouvoir retrouver une valeur par son nom ou son ISIN,
  comme dans le moteur de recherche d'un courtier.

## Critères d'acceptation

1. **Comparatif** — l'ADR `docs/adr/0009-fournisseur-donnees-marche.md` (créé avec `/new-adr`,
   référencé dans `docs/adr/README.md`) compare **3 à 5 fournisseurs** dans un tableau, sur ces
   critères :
   - cours actuels sur Euronext Paris : actions et ETF éligibles PEA, délai (temps réel, différé,
     fin de journée) ;
   - historique quotidien : profondeur, cours bruts et/ou ajustés, dividendes et opérations sur
     titres (divisions, attributions gratuites) ;
   - couverture de l'univers PEA : petites valeurs et Euronext Growth, autres places européennes,
     ETF éligibles PEA dont les benchmarks CAC 40 et MSCI World ;
   - identification : par ISIN, par ticker (et suffixe de place), recherche ISIN → ticker et
     recherche par nom ;
   - quotas de l'offre gratuite (appels par minute / jour / mois, requêtes groupées possibles ?)
     confrontés au besoin estimé : rafraîchissement quotidien d'environ 50 lignes et benchmarks,
     plus le chargement initial de leur historique ; tarif payant pour information ;
   - conditions d'utilisation : usage personnel autorisé ? stockage en cache / en base autorisé ?
     redistribution interdite ? (citer l'extrait pertinent) ;
   - fiabilité et pérennité : API officielle ou non, ancienneté, historique de ruptures,
     authentification.

   Chaque affirmation sur un fournisseur est **sourcée** (lien vers la page officielle et date de
   consultation) ; une information introuvable est notée « non documenté », jamais devinée.
   Un fournisseur non officiel (API non documentée, scraping) peut figurer comme point de
   comparaison, mais ses conditions d'utilisation sont analysées comme pour les autres.

2. **Panel de test** — le spike porte sur une poignée de valeurs d'Euronext Paris, dont l'ISIN et
   le ticker sont vérifiés sur le site d'Euronext avant les appels :
   - une action du CAC 40 avec des attributions gratuites régulières (ajustement de
     l'historique) : Air Liquide (`FR0000120073`) ;
   - une action du CAC 40 ayant changé de nom et de ticker (continuité de l'historique) :
     TotalEnergies (`FR0000120271`, ex-Total `FP`) ;
   - un ETF MSCI World éligible PEA domicilié hors de France : Amundi MSCI World (`CW8`,
     `LU1681043599`) ;
   - un ETF MSCI World éligible PEA récent (historique court, ISIN irlandais) : iShares MSCI World
     Swap PEA (`WPEA`, `IE0002XZSHO1`) ;
   - un ETF CAC 40 (benchmark) : Amundi CAC 40 UCITS ETF Dist (`FR0007052782`) ;
   - une petite valeur peu liquide d'Euronext Growth Paris, choisie au début du spike (couverture
     hors grandes capitalisations, jours sans échange) ;
   - une action européenne éligible PEA cotée hors de Paris : ASML (`NL0010273215`, Euronext
     Amsterdam).

3. **Spike sur 1 ou 2 fournisseurs** — les mieux classés du comparatif. Pour chaque valeur du
   panel, l'ADR consigne (section « Résultats du spike », date d'exécution) :
   - la résolution de l'identifiant (ISIN → symbole du fournisseur, ou échec) ;
   - le cours actuel renvoyé : prix, devise, horodatage, délai constaté ;
   - la plus ancienne date d'historique quotidien disponible ;
   - l'exactitude : clôture **non ajustée** comparée à la clôture officielle Euronext sur
     5 dates réparties sur plusieurs années (écart toléré : 0 au centime ; tout écart est noté et
     expliqué) ;
   - la présence de cours ajustés et des dividendes / opérations sur titres ;
   - le nombre d'appels consommés et les quotas observés (en-têtes, erreurs 429).

4. **Spike jetable et sans secret**
   - Le code du spike vit hors du dépôt (répertoire temporaire de la session), sans dépendance
     (script Node, `fetch` natif) ; il n'est ni commité ni référencé par le code de production.
   - Une clé d'API éventuelle est créée par le propriétaire, transmise uniquement par une
     variable d'environnement (`MARKET_DATA_SPIKE_API_KEY`) et n'apparaît ni dans un fichier du
     dépôt, ni dans une sortie de commande, ni dans une conversation avec l'agent. Une URL qui
     porte la clé en paramètre n'est jamais affichée telle quelle (clé masquée).
   - Les réponses brutes des fournisseurs ne sont pas commitées (redistribution) : l'ADR n'en
     garde que les valeurs utiles à la comparaison.

5. **Décision** — l'ADR (statut `Proposé`) recommande un fournisseur principal et, s'il y en a un,
   un fournisseur de secours ou de vérification, avec :
   - la justification au regard des critères et des résultats du spike ;
   - les exigences qui en découlent pour le futur `MarketDataProvider` : identifiant pivot (ISIN
     ou ticker + place), cours ajustés ou bruts, durée de cache et fréquence de rafraîchissement
     compatibles avec les quotas et les conditions d'utilisation, configuration de la clé ;
   - les risques acceptés et les signaux qui imposeraient de réévaluer (changement de conditions,
     arrêt de l'offre gratuite, données fausses).

   Le statut passe à `Accepté` uniquement sur décision explicite du propriétaire.

6. **Capitaliser** — `docs/ROADMAP.md` indique l'avancement de l'étape 2.0 ; les pièges
   rencontrés pendant le spike (format, fuseau horaire, ajustements, quotas…) sont écrits dans
   l'ADR ou dans la section « Pièges connus » du `CLAUDE.md` concerné.

7. **Livraison** — branche dédiée, commit `docs(market): ...`, `npm run check` vert, PR avec la
   section « Pour les prochains agents ».

## Hors périmètre

- Port `MarketDataProvider`, adaptateur, cache en base et job de rafraîchissement : étape
  suivante, après acceptation de l'ADR.
- Temps réel / streaming, données intrajournalières, carnet d'ordres.
- Fondamentaux, news, données sectorielles et géographiques des ETF (répartition du dashboard) :
  à traiter avec les besoins correspondants.
- Taux de change : les valeurs du panel cotent en EUR ; le besoin viendra avec des titres en
  devise sur le compte-titres.

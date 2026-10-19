# Phase 2.3 — Profil investisseur, portefeuille déclaré, éligibilité PEA

## Contexte

Recentrage du 2026-10-19 (ROADMAP, Phase 2) : le propriétaire place une **somme unique** sur son
PEA et passe ses ordres lui-même chez son courtier (BoursoBank). L'application doit fournir à
l'agent de conseil (Phase 3) :

- **ce que veut le propriétaire** : son profil ;
- **ce qu'il détient déjà** : son portefeuille déclaré ;
- **ce qu'il a le droit d'acheter sur un PEA** : l'éligibilité des valeurs.

Aucun suivi des transactions, aucun PRU recalculé. Les lignes sont **déclarées** telles que le
courtier les affiche.

## Critères d'acceptation

### 1. Profil investisseur (un seul profil, mono-utilisateur)

1. Champs du profil :
   - **somme à placer** : montant unique en EUR (> 0), placé en une fois (choix du propriétaire :
     pas de versements réguliers) ;
   - **horizon** de placement, en années (1 à 40) ;
   - **profil de risque choisi directement** (choix du propriétaire) : prudent, équilibré,
     dynamique ou offensif. Chaque profil a une description documentée qui sera transmise à
     l'agent : baisse annuelle supportable indicative (≈ 10 %, 20 %, 30 %, 40 % et plus) et
     part indicative d'actions ;
   - **préférences** facultatives :
     - secteurs ou valeurs à exclure (liste) ;
     - texte libre d'objectifs, 500 caractères au plus, traité comme une donnée non fiable
       quand il sera transmis à l'agent.
   - **date d'ouverture du PEA** (optionnelle) : l'ancienneté de 5 ans conditionne la fiscalité.
2. Création et mise à jour (PUT idempotent), lecture. Les validations sont testées, et un profil
   absent renvoie 404 `profile.not-found`.

### 2. Portefeuille déclaré (PEA)

3. Une **ligne** contient :
   - une valeur du référentiel (ISIN + MIC) ;
   - une quantité (entier > 0) ;
   - un prix moyen d'achat (optionnel, EUR, 4 décimales au plus) recopié depuis le courtier ;
   - la date de dernière mise à jour.
   Une valeur n'apparaît qu'une fois. On peut ajouter, modifier ou supprimer une ligne.
4. **Espèces** disponibles sur le PEA : montant ≥ 0, optionnel.
5. Ajouter une ligne **fait suivre la valeur** par le module `market`, sans appel externe : son
   historique d'1 an et le rattrapage des jours manqués suivent.
6. **Valorisation**, en `BigDecimal` HALF_EVEN au centime :
   - valeur de chaque ligne = quantité × dernière clôture connue, avec la date de cette clôture ;
   - plus-value latente si le prix moyen d'achat est renseigné ;
   - poids de chaque ligne ;
   - total, espèces comprises.
   Une valeur sans clôture est signalée « cours indisponible », jamais valorisée à 0.

### 3. Éligibilité PEA

7. Chaque instrument a une éligibilité `ELIGIBLE`, `NOT_ELIGIBLE` ou `UNKNOWN`, et une source
   `RULE` ou `OWNER` :
   - **Actions** : le pays de l'ISIN est dans l'UE ou l'EEE → `ELIGIBLE` (« probable : vérifier
     chez le courtier ») ; sinon `NOT_ELIGIBLE`. Limite documentée : le pays de l'ISIN n'est pas
     toujours celui du siège, et les foncières SIIC sont exclues du PEA sans que la règle le sache.
   - **ETF et fonds** : `UNKNOWN` par défaut, car l'éligibilité ne se déduit pas de l'ISIN ;
     les benchmarks configurés (CW8, WPEA, CAC) sont `ELIGIBLE`.
   - **Le propriétaire peut corriger** une éligibilité (source `OWNER`), et la règle ne l'écrase
     jamais.
8. La recherche de valeurs et le détail d'une valeur exposent l'éligibilité.

### 4. API et UI

9. API sous `/api/v1/` :
   - `profile` : GET/PUT ;
   - `portfolio` : lignes, espèces, valorisation ;
   - `market` : PUT de l'éligibilité.
   Les tags sont ajoutés à `OpenApiContractIT`, et le contrat et le client sont régénérés.
10. UI Angular (Material), en français :
    - page **Mon profil** : formulaire, avec la description du profil de risque choisi ;
    - page **Mon portefeuille** : tableau des lignes (valeur, quantité, cours et date, valeur,
      poids, plus-value), total, espèces, ajout avec **recherche par nom, ISIN ou ticker** et
      badge d'éligibilité PEA, modification, suppression avec confirmation ;
    - accessibilité, états vide, chargement et erreur ;
    - montants formatés en français, sans calcul flottant côté front.
11. Tests :
    - domaine (profils de risque, valorisation, règle d'éligibilité, avec la liste UE/EEE testée) ;
    - intégration (vraie base) et web ;
    - Vitest ;
    - E2E Playwright : remplir le profil, ajouter une ligne trouvée par recherche, voir la
      valorisation.

## Hors périmètre

- Transactions, PRU recalculé, plus-values réalisées, import CSV ou documents (abandonnés).
- Compte-titres ordinaire (CTO) : PEA seul.
- Plafond de versements de 150 000 € : non suivi, faute de transactions.
- Indicateurs (volatilité, pire baisse…) et dashboard : 2.4.

## Écarts et précisions d'implémentation

Aucun choix produit n'a changé. Précisions techniques prises pendant l'implémentation :

- **Modules** : profil et portefeuille déclaré dans le seul module `portfolio` (aucune dépendance
  entre eux, un seul propriétaire) ; l'éligibilité est un attribut de l'instrument dans `market`.
  `portfolio` n'appelle `market` que par `market.application` (`MarketQueries.details`,
  `InstrumentTracking.track`) et réutilise ses types du domaine (`InstrumentId`, `Instrument`).
  L'API `portfolio` réutilise `InstrumentSummaryResponse` / `PeaEligibilityResponse` de
  `market.api` : un seul type dans le client généré pour la recherche et les lignes.
- **API** : un seul tag `portfolio` (convention « un tag par module ») pour `/api/v1/profile` et
  `/api/v1/portfolio` ; la correction d'éligibilité est sous le tag `market`
  (`PUT /api/v1/market/instruments/{id}/pea-eligibility`, contrôleur séparé à cause de la limite
  Checkstyle de couplage). Ajout de `GET /api/v1/profile/risk-profiles` : les libellés et
  descriptions des profils de risque viennent du backend (enum `RiskProfile`), seule source du
  texte transmis à l'agent. Codes d'erreur : `profile.not-found` (404),
  `portfolio.line.not-found` (404), `portfolio.line.duplicate-instrument` (422),
  `portfolio.line.currency-not-supported` (422), `instrument.not-found` (404).
- **Éligibilité** : seule la correction du propriétaire est stockée
  (`instrument.pea_eligibility_correction`, V4) ; la règle est appliquée **à la lecture**. Ni le
  rafraîchissement hebdomadaire du référentiel (dont l'upsert n'écrit pas ces colonnes) ni une
  évolution de la règle ne peuvent donc écraser une correction (testé par
  `PeaEligibilityPersistenceIT`). La liste `investai.market.benchmarks` sert aussi de liste d'ETF
  éligibles : n'y mettre que des ETF éligibles au PEA. Pas de retour à la règle une fois corrigé
  (le propriétaire peut répondre « Je ne sais pas » = `UNKNOWN` / `OWNER`) : point ouvert.
- **Persistance** : `JdbcClient`, comme le module `market` (pas d'entité JPA, ADR-0005 respecté :
  Flyway, `numeric(19, 4)` + devise). Le profil et les espèces sont une ligne unique de clé
  constante (`singleton boolean PRIMARY KEY CHECK (singleton)`) au lieu d'un `uuid` : la base
  garantit l'unicité et le PUT est un upsert atomique.
- **Montants** : chaînes décimales dans les requêtes comme dans les réponses (`"20000.50"`),
  jamais de nombre JSON flottant. Bornes : somme et espèces 13 chiffres entiers et 2 décimales,
  prix moyen 15 + 4 décimales, quantité entière ≤ 1 000 000 000. Un nombre décimal envoyé pour un
  entier (`"quantity": 1.5`) est refusé en 400 au lieu d'être tronqué
  (`spring.jackson.deserialization.accept-float-as-int=false`, global).
- **Valeurs en euros seulement** : une valeur qui ne cote pas en euros est refusée à l'ajout
  (422) ; une clôture dans une autre devise est laissée hors de la valorisation (« cours
  indisponible »), jamais mélangée au total en euros.
- **Valorisation** : valeur d'une ligne arrondie une fois (HALF_EVEN, au centime) ; total = somme
  des valeurs arrondies + espèces ; plus-value = quantité × (clôture − prix moyen), arrondie une
  fois (elle peut différer d'un centime de la différence de deux montants arrondis) ; poids en %
  à 2 décimales (leur somme peut ne pas faire exactement 100) et absents si le total est nul. Ajout
  de la plus-value en % (`unrealizedGainPercent`). Une ligne sans clôture est exclue du total,
  comptée dans `unpricedLines`, et `complete` vaut `false`. Lignes triées par nom de valeur.
- **Profil** : exclusions bornées à 20 × 100 caractères, liste absente = aucune ; caractères de
  contrôle refusés (sauf retour à la ligne et tabulation dans les objectifs : PostgreSQL refuse le
  caractère nul, et un caractère invisible n'a rien à faire dans un texte relu par l'agent) ; le
  texte reste sinon stocké tel quel. Date d'ouverture du PEA : pas dans le futur
  (`@PastOrPresent`, horloge système du validateur, et contrôle du formulaire).
- **Suppression d'une ligne** : la valeur reste suivie par le module `market` (pas de « désuivi »).
- **UI** : routes `/profil` et `/portefeuille`. Les montants sont formatés et analysés comme du
  texte (`shared/format/decimal.ts`, espace fine insécable, virgule) : `Intl.NumberFormat`
  n'accepte une chaîne qu'à partir de la lib TypeScript ES2023, non activée (`tsconfig` protégé).
- **Vérifications** : E2E Playwright sur API simulée (convention du dépôt) ; la vérification dans
  la vraie application (`/verify-app`) n'a pas pu être faite, le serveur MCP du navigateur ne
  répondant pas dans cette session.

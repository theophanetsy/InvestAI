# Phase 2.4 — Indicateurs calculés et dashboard

## Contexte

L'agent de conseil (Phase 3) doit justifier ses propositions par des chiffres **calculés par
l'application**, jamais produits par le modèle. Cette étape calcule, pour chaque valeur de Paris
et pour le portefeuille déclaré (2.3), des indicateurs simples et standard. Elle les expose à
l'API, et plus tard aux tools de l'agent, et les affiche dans un premier dashboard.

Les données sont les clôtures brutes collectées depuis la 2.1 et la 2.2 :

- **valeurs suivies** : 1 an d'historique ;
- **autres valeurs** : historique qui se constitue depuis la mise en service.

## Critères d'acceptation

### 1. Indicateurs par valeur (domaine, testés sur des séries synthétiques)

1. **Fenêtres** : 1 mois, 3 mois, 6 mois, 1 an (21, 63, 126, 252 séances).
   - Un indicateur n'est calculé que si la fenêtre a au moins 80 % de ses séances. Sinon il vaut
     « historique insuffisant », jamais une valeur approximative.
2. **Performance du cours** : variation de la clôture sur chaque fenêtre.
   - Libellé « hors dividendes ».
   - Non ajustée des divisions et attributions gratuites (critère 5).
3. **Risque** (sur 1 an) :
   - **volatilité** annualisée : écart-type des rendements quotidiens × √252 ;
   - **pire baisse** : plus forte chute entre un plus haut et un plus bas ultérieur.
4. **Face au MSCI World** (CW8) et au CAC 40 (CAC), sur 1 an :
   - surperformance ;
   - corrélation ;
   - bêta.
   **Liquidité** : montant moyen échangé par jour sur 3 mois (volume × clôture), d'après les
   fichiers Euronext.
5. **Divisions et attributions gratuites : aucun ajustement** (choix du propriétaire, pour
   rester simple). Les indicateurs portent sur les **clôtures brutes**.
   - Limite documentée dans la spec, l'API et le dashboard : l'année d'une division ou d'une
     attribution gratuite (exemple : Air Liquide, 1 action gratuite pour 10), la performance et la
     pire baisse de la valeur sont faussées par une chute artificielle du cours.
   - La description des indicateurs transmise à l'agent en Phase 3 le dira aussi.
   - Pas de détection de ces sauts dans cette étape.
6. **Calcul quotidien** après la collecte, stocké en base. Le recalcul est idempotent. Cela permet
   à l'agent de **filtrer tout Paris** sans recalcul : par exemple les valeurs éligibles PEA,
   de volatilité inférieure à 25 %, et liquides.

### 2. Indicateurs du portefeuille déclaré

7. Indicateurs calculés :
   - poids de la plus grosse ligne ;
   - concentration (indice de Herfindahl) ;
   - nombre de lignes ;
   - part des espèces ;
   - part d'ETF et part d'actions ;
   - volatilité et pire baisse **du portefeuille actuel, comme s'il avait été détenu depuis un
     an** (libellé explicite) ;
   - matrice des corrélations entre les lignes.
8. Les valeurs sans historique suffisant sont exclues des calculs, et leur exclusion est signalée.

### 3. API et dashboard

9. API sous `/api/v1/` :
   - indicateurs d'une valeur ;
   - filtre sur tout Paris (éligibilité, volatilité maximale, liquidité minimale, performance,
     tri, 50 résultats au plus) ;
   - indicateurs du portefeuille.
   Le contrat et le client sont régénérés.
10. Dashboard Angular (la page « Dashboard » existante) :
    - valeur totale et date des cours ;
    - tableau des lignes avec leurs indicateurs ;
    - **courbe du portefeuille actuel sur 1 an, comparée à CW8 et à l'ETF CAC 40** (choix du
      propriétaire), sur une base 100 ;
    - cartes de risque (volatilité, pire baisse, concentration) ;
    - avertissements (historique insuffisant, cours possiblement non ajusté).
    Le graphique est dessiné **sans nouvelle dépendance** (SVG), ou bien avec une bibliothèque
    justifiée par un ADR.
11. Tests :
    - unitaires, avec des valeurs de référence calculées à la main sur des séries synthétiques ;
    - intégration et web ;
    - Vitest ;
    - E2E du dashboard.

## Hors périmètre

- Dividendes, donc pas de performance « dividendes réinvestis ».
- Secteurs et pays d'activité (non disponibles gratuitement).
- Prévisions de cours, scores et recommandations : Phase 3, avec l'agent.

## Écarts et précisions d'implémentation

Aucun choix produit n'a changé (clôtures brutes sans ajustement, courbe du portefeuille actuel
comparée à CW8 et à l'ETF CAC 40). Le critère 2 disait « Ajustée des divisions et attributions
gratuites (critère 5) », en contradiction avec le critère 5 (aucun ajustement, choix du
propriétaire) : corrigé en « Non ajustée ».

### Définitions retenues (domaine `market.domain`, JDK uniquement)

- **Date de référence** (`asOf`) : dernière séance ayant une clôture **avant aujourd'hui** (heure
  de Paris). Une clôture du jour (EODHD le soir, avant le fichier Euronext du lendemain) attend le
  lendemain : toutes les valeurs partagent la même dernière séance.
- **Fenêtres** : les N séances du calendrier Euronext (`EuronextCalendar`, jours fériés exclus)
  qui se terminent à `asOf`, précédées de la séance de référence, soit N + 1 clôtures
  (`YearWindow` : 253 séances pour un an). Une séance sans clôture reste dans la fenêtre.
- **Règle des 80 %** : au moins 80 % des N + 1 séances ont une clôture, arrondi au supérieur
  (18 sur 22 pour un mois, 52 sur 64, 102 sur 127, 203 sur 253 pour un an), sinon
  `INSUFFICIENT_HISTORY`. Pour une comparaison à un indice, l'historique de la valeur **et** celui
  de l'ETF doivent suffire.
- **Performance** : variation entre la première et la dernière clôture disponibles de la fenêtre
  (la séance de référence, sauf si elle manque), en %.
- **Rendements quotidiens et trous** : un rendement n'existe qu'entre deux séances **consécutives
  du calendrier** ayant chacune une clôture. Une séance manquante supprime les deux rendements qui
  l'entourent ; elle n'est jamais interpolée ni fusionnée en un rendement sur deux séances (qui
  fausserait la volatilité « par séance » annualisée par √252).
- **Volatilité** : écart-type **échantillon** (n − 1) des rendements disponibles sur un an × √252,
  en %. **Pire baisse** : plus forte chute d'un plus haut vers un plus bas ultérieur parmi les
  clôtures disponibles de l'année, en % (≤ 0).
- **Surperformance** : performance à un an de la valeur moins celle de l'ETF, en points (calculée
  sur les performances non arrondies). **Corrélation** (Pearson) et **bêta** (covariance / variance
  de l'ETF) : sur les séances où les deux rendements existent. Une série constante rend la
  corrélation `NOT_COMPUTABLE` (le bêta d'une valeur constante vaut 0, celui contre un ETF
  constant est `NOT_COMPUTABLE`).
- **Liquidité** : moyenne de clôture × volume sur les 63 dernières séances, **volumes Euronext
  uniquement** (définition différente chez EODHD, spec 2.2), dans la devise de la valeur ; il faut
  un volume Euronext sur 80 % des 63 séances (51). Conséquence : « historique insuffisant » pour
  toutes les valeurs jusqu'à environ trois mois de collecte après la mise en service de la 2.2.
- **Précision** : prix et ratios de prix en `BigDecimal` (`MathContext.DECIMAL64`), arrondis
  HALF_EVEN à 2 décimales (pourcentages, montants) ; statistiques des rendements (volatilité,
  corrélation, bêta) en `double`, puis arrondies HALF_EVEN à 2 décimales. Tout est exposé en
  **chaîne décimale** avec un **statut** : `AVAILABLE`, `INSUFFICIENT_HISTORY` (moins de 80 %) ou
  `NOT_COMPUTABLE` (indéfini), jamais une valeur approchée ni un zéro de remplacement.

### Calcul quotidien et filtre de tout Paris (critères 6 et 9)

- Table `instrument_indicator` (migration V6) : une ligne par valeur, une valeur et un statut par
  indicateur (contrainte : valeur présente exactement si `AVAILABLE`), valeurs `numeric` sans
  précision (une cotation aberrante ne doit pas faire échouer le calcul de tout Paris), index sur
  `daily_price (trading_date)` et `(collected_at)`.
- `IndicatorComputation.refresh()` est appelé par les jobs du marché **après** la collecte
  Euronext (même si rien n'est collecté) et après la maintenance EODHD (historique chargé, séance
  rattrapée) ; désactivé dans les tests comme les autres jobs. **Idempotent** : rien n'est calculé
  si la dernière exécution couvre déjà `asOf` et qu'aucune clôture n'a été écrite depuis ; sinon
  toutes les lignes sont remplacées (upsert en lot, une transaction). Horodaté au **début** du
  calcul : une clôture écrite pendant le calcul déclenche un nouveau calcul au passage suivant.
  Verrou par cas d'usage (`tryLock`, comme la collecte). Clôtures lues par lots de 200 valeurs.
- **Mesures** (`IndicatorPerformanceIT`, 1 300 actions + 2 ETF × 253 séances = 337 218 clôtures
  synthétiques, PostgreSQL Testcontainers, poste Windows du propriétaire) : calcul complet
  **~2,0 s**, filtre de tout Paris **~0,14 s** ; l'insertion des clôtures de test prend ~10 s. Le
  test échoue au-delà de 2 min (régression grossière seulement).
- **Filtre** `GET /api/v1/market/indicators` : lit la table, **filtre et trie en mémoire** (~1 300
  lignes) parce que l'éligibilité PEA est résolue en Java (correction du propriétaire ou règle,
  spec 2.3), jamais recalculée. Paramètres : `peaEligibility`, `maxVolatility`,
  `minAverageDailyTradedAmount` (en euros : une valeur cotée dans une autre devise ne passe
  jamais ce filtre et est classée en dernier par le tri « liquidité »), `performanceWindow` (un an
  par défaut), `minPerformance`, `sort` (`PERFORMANCE`, `VOLATILITY`, `LIQUIDITY`,
  `MAX_DRAWDOWN`), `limit` (1 à 50, 50 par défaut). Une valeur dont l'indicateur filtré n'est pas
  disponible ne passe jamais le filtre ; un tri met les valeurs sans l'indicateur en dernier
  (égalités : nom puis ISIN). Réponse : `matchCount` (avant limite) et `results`. Décimaux en
  chaînes validées par motifs ancrés (jamais analysés s'ils sont trop longs), paramètre invalide →
  400 `validation.failed` avec le nom du champ.
- `GET /api/v1/market/instruments/{id}/indicators` : indicateurs stockés ; 404
  `instrument.not-found`, ou `indicators.not-computed` avant le premier calcul qui a vu la valeur.
- **Indices de référence** : `investai.market.indicators.msci-world` (CW8) et `.cac40` (CAC),
  vérifiés au démarrage : ils doivent figurer dans `investai.market.benchmarks` (valeurs suivies,
  donc un an d'historique).

### Portefeuille déclaré (critères 7 et 8)

- **Calculé à la demande** (`GET /api/v1/portfolio/indicators`), pas stocké : quelques lignes,
  lues en deux requêtes, toujours cohérentes avec les lignes déclarées à l'instant (un stockage
  devrait être invalidé à chaque modification). Mêmes classes du domaine que le calcul quotidien,
  même `asOf` : les indicateurs d'une ligne sont ceux qu'aurait la valeur dans la table.
- **Composition** : plus grosse ligne = poids le plus élevé de la valorisation 2.3 (espèces dans le
  total) ; **Herfindahl** = somme des carrés des poids des lignes valorisées dans la **partie
  investie** (espèces exclues), 4 décimales ; parts d'actions, d'ETF et d'« autres » (fonds,
  autres) dans le total espèces comprises ; part des espèces `NOT_COMPUTABLE` si elles ne sont
  pas déclarées.
- **Risque « comme s'il avait été détenu depuis un an »** : les quantités actuelles des lignes
  retenues rejouées sur les 253 séances, **espèces exclues** ; une séance où une ligne retenue n'a
  pas de clôture n'a pas de valeur de portefeuille (trou), puis la règle des 80 % s'applique à la
  série du portefeuille. Lignes **exclues et signalées** (`exclusion`) : `NOT_VALUED` (pas de
  clôture en euros) ou `INSUFFICIENT_HISTORY` (moins de 80 % des clôtures de l'année). Sans aucune
  ligne retenue : `INSUFFICIENT_HISTORY` si une ligne manque d'historique, `NOT_COMPUTABLE` sinon.
- **Courbe base 100** : à partir de la première séance où le portefeuille et chaque ETF d'historique
  suffisant ont une valeur ; un ETF d'historique insuffisant est absent de la courbe ; un trou reste
  un trou. **Matrice de corrélations** entre les lignes retenues, dans l'ordre d'affichage.
- **Partage des DTO** : `IndicatorResponse` et `IndicatorsResponse` de `market.api` sont réutilisés
  par l'API du portefeuille (un seul type dans le client généré, comme `InstrumentSummaryResponse`
  en 2.3) : la liste autorisée de la règle ArchUnit `PORTFOLIO_USES_MARKET_THROUGH_ITS_APPLICATION`
  a été étendue à ces deux DTO.

### Dashboard (critère 10)

- Page « Tableau de bord » : cartes (valeur totale et date des cours, risque, concentration,
  répartition), avertissements (lignes exclues, historique insuffisant, mention permanente
  « cours possiblement non ajustés »), courbe, tableau des lignes (performances 1/3/6/12 mois,
  volatilité, pire baisse, bêta MSCI World, prise en compte dans le risque), matrice de
  corrélations ; invitation vers « Mon portefeuille » sans ligne déclarée.
- **Courbe en SVG dessiné à la main, sans dépendance** : titre et description reliés
  (`role="img"`, `aria-labelledby`, `aria-describedby`), tableau des 253 valeurs dans un
  `<details>`, séries distinguées par le motif du trait (plein, tirets, pointillés) en plus de la
  couleur, couleurs du thème Material (`--mat-sys-*`, thème clair ou sombre selon le système ;
  `--mat-sys-secondary` plutôt que `tertiary`, égal au primaire avec une seule palette). Les séances
  sont régulièrement espacées en abscisse (pas le temps calendaire). Les valeurs base 100 ne
  deviennent des nombres que pour placer les points ; tout chiffre affiché reste la chaîne de l'API.

### Points ouverts

- Pas de détection des divisions ni des attributions gratuites (choix du propriétaire) : la
  description des indicateurs donnée à l'agent en Phase 3 devra le rappeler (déjà dans le contrat
  OpenAPI et sur le dashboard).
- Liquidité indisponible tant que trois mois de volumes Euronext ne sont pas collectés.
- Le filtre n'a pas de critère de type (action / ETF) : à ajouter si l'agent en a besoin.
- Pas d'écran pour le filtre de tout Paris (utilisé par l'agent en Phase 3).

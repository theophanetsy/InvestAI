# 0009 — Données de marché : fichiers différés Euronext (MiFIR) + EODHD gratuit

- **Statut** : Proposé
- **Date** : 2026-10-19

## Contexte

Les données de marché alimentent le dashboard (valorisation, courbe d'évolution), les simulations,
les backtests et les tools de l'agent ([spec Phase 2.0](../specs/phase-2-00-market-data.md)).
Contraintes du propriétaire :

- **Budget : 0 €.**
- **Univers** : toutes les actions d'Euronext Paris (petites valeurs et Euronext Growth compris)
  et les ETF éligibles PEA cotés à Paris sont indispensables ; les autres places Euronext sont un
  plus ; Xetra, Madrid, etc. ne sont pas requises. Retrouver une valeur par son nom ou son ISIN.
- **Placement moyen / long terme** : une clôture quotidienne suffit (un différé d'une heure est
  acceptable), 1 à 2 ans d'historique suffisent ; l'historique s'allonge ensuite par la collecte.
- Usage strictement personnel, données stockées en base (cache), pas de redistribution.

Le besoin estimé : ~50 lignes et benchmarks rafraîchis une fois par jour, plus le chargement
initial de leur historique.

## Options étudiées

Recherche documentaire du 2026-10-19 (sources ci-dessous), puis spike sur les deux options
retenues (section suivante).

| Source | Paris (actions, Growth, ETF PEA), offre gratuite | Historique gratuit | Quota gratuit | Conditions d'utilisation |
|---|---|---|---|---|
| **Fichiers différés Euronext (MiFIR)** | ✅ toutes les transactions de la place ; aussi Amsterdam, Bruxelles, Milan, Dublin, Lisbonne, Oslo | ❌ séance précédente uniquement | non documenté | ✅ « free of charge … no restrictions on the Use, except in the event of distribution » |
| **EODHD** | ✅ 1 369 symboles PA (628 actions, 721 ETF), ISIN | 1 an, brut + ajusté, dividendes, divisions | 20 appels/jour + 500 une fois | ✅ « permitted to store, manipulate, and analyze the data for private, non-commercial purposes » |
| Yahoo Finance (non officiel) | ✅ (WPEA testé) | depuis 2000, ajusté | non publié ; vagues de 429 en 2025 | ❌ « access or collect data … using any automated means … without our express, prior permission » |
| Site live.euronext.com | ✅ | ~2 ans (export manuel) | — | ❌ robots interdits sans accord écrit ; seule « a single … copy … for your personal, non-commercial use » |
| Twelve Data | ❌ gratuit « US only » ; Paris dès Grow (29 $/mois) | — | 800 crédits/jour | gratuit : « Internal non-display usage » |
| Alpha Vantage | non documenté (Paris absent de la doc) | 100 jours, brut | 25 appels/jour | usage personnel |
| Financial Modeling Prep | ❌ ~87 symboles US ; Euronext dès 99 $/mois | — | 250 appels/jour | copie interdite sans accord écrit |
| Marketstack | Paris listé, WPEA absent | 1 an | 100 requêtes/**mois** | non documenté |
| Finnhub | ❌ Euronext dès 199,99 $/mois | — | 60/min | données à supprimer en fin d'abonnement |
| Tiingo | ❌ aucune place Euronext | — | — | — |
| Stooq | non démontrée | — | clé obtenue par captcha (mars 2026) | introuvables |

### Option A — Fichiers différés Euronext (clôture quotidienne) + EODHD gratuit (référentiel, historique, OST)

- ➕ Source **officielle** pour la clôture : Euronext doit publier gratuitement ces données
  (article 13 du règlement MiFIR) ; usage libre hors redistribution.
- ➕ Un seul téléchargement par place et par jour couvre **toute** la place : aucune limite sur le
  nombre de lignes du portefeuille.
- ➕ EODHD (conditions claires, stockage perso autorisé) apporte ce que les fichiers n'ont pas :
  noms, recherche, 1 an d'historique, dividendes et divisions, rattrapage des jours manqués.
- ➖ Deux adaptateurs à écrire et à maintenir ; format CSV MiFIR non documenté par Euronext.
- ➖ Fichier disponible pour la **séance précédente seulement** : un jour non collecté est perdu
  côté Euronext (rattrapé par EODHD).
- ➖ Téléchargement automatisé : zone grise (voir Décision).

### Option B — EODHD gratuit seul

- ➕ Un seul adaptateur, API documentée, clé personnelle.
- ➖ 20 appels/jour, téléchargement groupé payant : impossible de rafraîchir ~50 lignes chaque jour.

### Option C — Yahoo Finance (non officiel)

- ➕ Couverture et historique les plus larges, sans clé.
- ➖ Accès automatisé explicitement interdit ; ruptures régulières (crumb 2023, 429 en 2025) ;
  qualité hors États-Unis reconnue imparfaite (option `repair` de yfinance). Inutile depuis que
  1 à 2 ans d'historique suffisent.

### Option D — Offre payante (EODHD « EOD Historical All-World », 19,99 €/mois)

- ➕ Tout en une API (historique profond, téléchargement groupé par place, 100 000 appels/jour).
- ➖ Hors budget. Reste la **porte de sortie** si l'option A devient impraticable.

## Résultats du spike (2026-10-19)

Scripts Node jetables hors du dépôt ; clé EODHD chargée par `node --env-file` depuis un fichier
hors du dépôt, masquée dans toutes les sorties. Référence : exports « Historical prices » de
live.euronext.com téléchargés à la main par le propriétaire (21/10/2024 → 16/10/2026).

Panel vérifié sur Euronext : Air Liquide (`FR0000120073`, AI, XPAR), TotalEnergies
(`FR0000120271`, TTE, XPAR), Gevelot (`FR0000033888`, ALGEV, ALXP — Euronext Growth, peu liquide),
ASML (`NL0010273215`, ASML, XAMS), Amundi MSCI World Swap (`LU1681043599`, CW8, XPAR), iShares
MSCI World Swap PEA (`IE0002XZSHO1`, WPEA, XPAR), Amundi CAC 40 Dist (`FR0007052782`, CAC, XPAR).

### Fichiers différés Euronext (séance du 16/10/2026)

- Téléchargement : `GET https://marketdata.euronext.com/data-reporting-service/trades-file/download/{EQUITIES|ETFS_FUNDS}/PREVIOUS_TRADING_DAY/{PAR|AMS|…}`,
  sans authentification, ZIP contenant un CSV. Paris actions : 7,9 Mo (85 Mo décompressé,
  483 023 transactions) ; Paris ETF : 0,8 Mo (42 429) ; Amsterdam actions : 3,8 Mo (244 270).
  Fichiers générés le matin de la séance suivante (entre ~8 h et ~9 h).
- Une ligne par transaction : ISIN (`MifidInstrumentID`), prix, quantité, devise, horodatage UTC,
  lieu (`Venue` : XPAR, ALXP, XAMS…), mécanisme (`MmtMarketMechanism` : 1 = carnet d'ordres
  central, enchères comprises ; 3 = carnet anonyme…). Première ligne : mention de copyright,
  avant l'en-tête.
- **Clôture = dernière transaction `MmtMarketMechanism = 1` sur le lieu de cotation** :
  **7/7 égales au centime** à la clôture officielle (actions, Growth, ETF, Amsterdam), et le
  nombre de transactions et de titres échangés correspond exactement à l'export officiel.
- Gevelot : 1 seule transaction ce jour-là ; un jour sans échange, l'ISIN est absent du fichier.

### EODHD gratuit (24 appels sur le quota)

- **Recherche par ISIN** : 7/7 trouvés ; plusieurs cotations par ISIN (AI.PA, AIL.XETRA,
  0NWF.LSE…) → filtrer par place. Symboles : `AI.PA`, `ALGEV.PA`, `ASML.AS`, `CW8.PA`…
- **Liste des symboles d'une place** en 1 appel (`exchange-symbol-list/PA`) : 1 369 symboles avec
  nom, type et ISIN (1 260 renseignés), dont 254 en `AL…` (Euronext Growth). Suffit pour une
  recherche locale par nom ou ISIN.
- **Historique** : exactement 1 an (depuis le 20/10/2025) malgré `from=2000-01-01`.
- **Exactitude** (clôture brute `close` vs export Euronext, 5 dates sur l'année disponible) :
  **30/30 cohérentes**. TotalEnergies, ASML, CW8, WPEA, CAC, Gevelot : égales au centime.
  Air Liquide : égales après le 08/06/2026, et avant cette date l'export Euronext vaut
  `close × 10/11` (ex. 173,04 → 157,30909) : **l'export Euronext ajuste l'historique des
  attributions gratuites**, EODHD fournit le cours brut « as traded ».
- `adjusted_close` EODHD intègre divisions **et dividendes** (Air Liquide 173,04 → 154,0065).
- **Opérations sur titres** : les 5 attributions gratuites d'Air Liquide depuis 2017 sont des
  « splits » 11/10 ; dividendes datés (TotalEnergies : trimestriels, y compris un dividende
  annoncé au 31/12/2026).
- **Petite valeur** : Gevelot a 211 barres pour 254 séances ; EODHD omet une partie des jours sans
  échange (35 présents sur 78), sans règle apparente.
- **Cours « temps réel »** : différé de 15 à 17 min ; pour une valeur sans échange du jour, le
  dernier cours connu (Gevelot : vendredi).
- **Quota** : 20 appels/jour puis consommation du bonus unique (500) ; `GET /api/user` (non
  décompté) donne la consommation. En-têtes `X-RateLimit-Limit: 1200` (par minute).
- La clé passe **dans l'URL** (`api_token=`) : toute URL journalisée la divulgue.

## Décision

**Option A**, sous réserve de l'acceptation par le propriétaire du point « zone grise » ci-dessous.

1. **Clôture quotidienne : fichiers différés Euronext (MiFIR).** Un job planifié télécharge chaque
   matin (après ~9 h, heure de Paris) les fichiers `PREVIOUS_TRADING_DAY` des segments utiles
   (`EQUITIES` et `ETFS_FUNDS` de `PAR`, `AMS`… seulement si le portefeuille y a des lignes),
   et retient pour chaque instrument suivi la dernière transaction `MmtMarketMechanism = 1` sur
   son lieu de cotation.
2. **Référentiel, historique, opérations sur titres, rattrapage : EODHD gratuit.**
   - Référentiel local : `exchange-symbol-list` par place (1 appel), rafraîchi chaque semaine ;
     la recherche par nom / ISIN se fait en base, sans appel externe.
   - Ajout d'un instrument : 1 an d'historique (`eod`) + dividendes (`div`) + divisions (`splits`),
     soit 3 appels.
   - Jour manqué (application éteinte, fichier indisponible) : rattrapage par `eod` avec
     `from`/`to` (1 appel par instrument, quelle que soit la durée du trou).
   - Historique au-delà d'1 an si besoin : import manuel des exports Euronext (~2 ans), via
     l'import CSV prévu en Phase 2, en tenant compte de leur ajustement (voir Exigences).
3. **Zone grise acceptée (à confirmer par le propriétaire)** : les conditions des données
   différées autorisent tout usage non redistribué, mais les conditions générales du site
   Euronext interdisent les robots « to navigate, search or determine the Euronext Website ».
   Le job se limite à **un GET direct par fichier et par jour**, avec un `User-Agent`
   identifiable, sans naviguer ni extraire de pages. Une demande de confirmation écrite à
   `databyeuronext@euronext.com` peut lever le doute (à l'initiative du propriétaire).

### Exigences pour le `MarketDataProvider` (étape suivante)

- **Identifiant pivot : ISIN + MIC** (`FR0000120073` + `XPAR`). Un ISIN a plusieurs cotations
  (CW8 : XPAR et ETFP ; Air Liquide : Paris, Xetra, Londres…). Le symbole EODHD (`AI.PA`) est un
  attribut de l'adaptateur, pas une clé du domaine.
- **Cours bruts « as traded » + opérations sur titres stockées à part** ; tout ajustement est
  calculé par l'application. La valorisation utilise le cours brut et la quantité réellement
  détenue : une attribution gratuite doit exister comme événement côté domaine (sinon la
  quantité détenue et le cours ne concordent plus après l'opération).
- **Jours sans échange** : pas de barre ce jour-là (ni chez Euronext, ni systématiquement chez
  EODHD) ; la valorisation reprend la dernière clôture connue et le signale.
- Les montants sont lus en `BigDecimal` depuis le texte (`"709.5500000"`), jamais via `double`.
- Fichier MiFIR : lecture **en flux** (85 Mo/jour pour Paris), colonnes repérées par leur nom,
  première ligne (copyright) ignorée, horodatages UTC.
- Clé EODHD : variable d'environnement (`INVESTAI_EODHD_API_KEY`), jamais dans une URL
  journalisée (logs HTTP client, messages d'exception, traces OpenTelemetry en Phase 3).
- Budget d'appels EODHD tenu par l'application (compteur quotidien, refus au-delà de ~15 appels
  pour garder une marge), consommation visible dans les logs.
- **Tests sans données réelles commitées** : publier des fichiers Euronext ou des réponses EODHD
  dans le dépôt serait une redistribution → jeux de données synthétiques au même format.

## Conséquences

- Positives :
  - 0 € ; deux sources aux conditions d'utilisation compatibles avec un usage perso et un cache en
    base ; clôtures officielles vérifiées au centime.
  - Nombre de lignes du portefeuille illimité pour la clôture quotidienne ; quota EODHD réservé
    aux événements rares (ajout d'instrument, rattrapage, référentiel).
  - Le port `MarketDataProvider` isole le domaine : passer à l'option D ne change que les
    adaptateurs.
- Négatives / dette :
  - Deux adaptateurs et un format CSV non documenté à surveiller.
  - Historique limité à 1 an automatiquement (2 ans avec l'import manuel) : backtests longs
    impossibles sans payer.
  - Zone grise sur le téléchargement automatisé des fichiers Euronext.
  - L'application doit tourner chaque jour ouvré pour collecter ; sinon rattrapage EODHD
    (1 appel par instrument).
  - Le serveur MCP et les skills Claude Code d'EODHD ne sont **pas** branchés sur l'agent de
    l'application (quota, contrôle des tools, objectif d'apprentissage) ; ils pourront servir
    de documentation pendant le développement de l'adaptateur, après relecture et version figée.
- Suivi (quand réévaluer) :
  - Fichier Euronext indisponible, protégé (anti-robots) ou format modifié ; refus d'Euronext.
  - Écart de clôture détecté entre les deux sources.
  - Changement des conditions ou du quota gratuit d'EODHD.
  - Besoin d'historique > 2 ans (backtests, Phase 4) ou de places hors Euronext → option D.

## Sources (consultées le 2026-10-19)

- Euronext : [conditions des données différées](https://www.euronext.com/sites/default/files/stld/terms_and_conditions_for_delayed_data.pdf),
  [téléchargement des transactions](https://marketdata.euronext.com/data-reporting-service/trades-file),
  [MiFID II](https://www.euronext.com/en/data/market-data/mifid-ii),
  [conditions du site](https://www.euronext.com/en/terms-use).
- EODHD : [tarifs](https://eodhd.com/pricing), [limites](https://eodhd.com/financial-apis/api-limits),
  [conditions](https://eodhd.com/financial-apis/terms-conditions),
  [recherche](https://eodhd.com/financial-apis/search-api-for-stocks-etfs-mutual-funds-and-indices),
  [serveur MCP](https://eodhd.com/financial-apis/mcp-server-for-financial-data-by-eodhd).
- Yahoo : [places et délais](https://help.yahoo.com/kb/SLN2310.html),
  [conditions](https://legal.yahoo.com/us/en/yahoo/terms/otos/index.html),
  [yfinance `repair`](https://ranaroussi.github.io/yfinance/advanced/price_repair.html),
  [yfinance #2422](https://github.com/ranaroussi/yfinance/issues/2422).
- Twelve Data : [tarifs](https://twelvedata.com/pricing), [places](https://twelvedata.com/exchanges),
  [conditions](https://twelvedata.com/terms).
- Alpha Vantage : [support](https://www.alphavantage.co/support/),
  [documentation](https://www.alphavantage.co/documentation/).
- FMP : [tarifs](https://site.financialmodelingprep.com/pricing-plans),
  [conditions](https://site.financialmodelingprep.com/terms-of-service).
- Marketstack : [tarifs](https://marketstack.com/pricing), [FAQ](https://marketstack.com/faq).
- Finnhub : [tarifs](https://finnhub.io/pricing), [conditions](https://finnhub.io/terms-of-service).
- Tiingo : [tarifs](https://www.tiingo.com/about/pricing).
- Stooq : [pandas-datareader #1012](https://github.com/pydata/pandas-datareader/issues/1012).

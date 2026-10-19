# Phase 2.1 — Données de marché : référentiel, collecte quotidienne, historique

## Contexte

Première brique de code de la Phase 2, qui applique l'[ADR-0009](../adr/0009-fournisseur-donnees-marche.md) :

- les **clôtures quotidiennes** viennent des fichiers différés gratuits d'Euronext (MiFIR) ;
- le **référentiel**, **1 an d'historique** et le **rattrapage** viennent de l'offre gratuite
  d'EODHD (20 appels par jour).

Le module `market` du backend collecte ces données avec des jobs planifiés et les stocke en
base. Le reste de l'application (portefeuilles en 2.2, dashboard, simulations, tools de l'agent)
ne lira **que la base**, jamais les fournisseurs.

Une seule personne utilise l'application, qui tourne sur son poste et peut être éteinte plusieurs
jours. La collecte doit donc **se rattraper toute seule** au redémarrage.

Périmètre de place : **Euronext Paris** (actions du marché réglementé `XPAR`, Euronext Growth
`ALXP`, ETF). Les autres places Euronext restent un bonus (hors périmètre, voir en fin de document).

### Découpage proposé pour la Phase 2

| Étape | Contenu |
|---|---|
| **2.1** (cette spec) | Module `market` : référentiel d'instruments, collecte des clôtures, historique, API de lecture et de suivi. |
| 2.2 | Comptes PEA / CTO et transactions (saisie manuelle), avec la recherche de valeurs dans l'UI. |
| 2.3 | Positions et valorisation : PRU, plus-values, éligibilité PEA, plafond de versements, attributions gratuites. |
| 2.4 | Import CSV des transactions. |
| 2.5 | Dashboard Angular : valeur, répartition, courbe d'évolution comparée aux benchmarks. |

## Critères d'acceptation

### 1. Modèle et schéma

1. **Migration Flyway** `V2__market_data.sql`, conforme aux conventions de `backend/CLAUDE.md`.
   - `instrument` : `id` uuid ; `isin` ; `mic` ; `ticker` ; `name` ; `type` (`STOCK`, `ETF`,
     `FUND`, `OTHER`) ; `currency` ; `tracked_since` (nul si la valeur n'est pas suivie) ;
     `history_loaded` (booléen) ; dates de création et de mise à jour.
     **Unicité sur `(isin, mic)`.**
   - `daily_price` : clé `(instrument_id, trading_date)` ; `close` en `numeric(19, 4)` ;
     `currency` ; `source` (`EURONEXT`, `EODHD`) ; `collected_at`.
   - `market_collection` : une ligne par fichier Euronext traité, avec la séance, le segment
     (`EQUITIES`, `ETFS_FUNDS`), la date de traitement et le nombre de clôtures retenues.
   - `eodhd_usage` : appels EODHD consommés par jour UTC.
2. **Domaine sans framework**. Le package `market.domain` contient les value objects (`Isin`
   avec contrôle de la clé de Luhn, `Mic`, `InstrumentId`, `DailyClose`…), le calendrier
   Euronext et les ports. Il ne connaît ni Spring, ni JPA, ni Jackson (règles ArchUnit
   existantes).
3. **Calendrier des séances Euronext** (domaine, testé unitairement sur 2025 à 2027) :
   - jours fermés : samedi, dimanche, 1er janvier, Vendredi saint, lundi de Pâques, 1er mai,
     25 et 26 décembre (Pâques calculé, pas codé en dur) ;
   - fournit la « séance précédente » d'une date et la liste des séances entre deux dates.

### 2. Référentiel d'instruments (EODHD)

4. Le référentiel est chargé en **1 appel** (`exchange-symbol-list/PA`) :
   - au démarrage s'il est vide ou vieux de plus de 7 jours ;
   - puis chaque semaine.
   Les symboles sans ISIN ou avec un ISIN invalide sont ignorés et leur nombre est journalisé.
   L'opération est idempotente : un upsert sur `(isin, mic)` ne crée jamais de doublon, et une
   valeur absente du nouveau référentiel est conservée avec son historique.
5. **Règle MIC** :
   - code EODHD commençant par `AL` → `ALXP` ; sinon → `XPAR` ;
   - type EODHD `Common Stock` → `STOCK`, `ETF` → `ETF`, `FUND` → `FUND`, sinon `OTHER`.
   Ces règles sont testées sur les 7 valeurs du panel de l'ADR, en données synthétiques.
6. **Recherche locale**, sans appel externe : par ISIN exact, ticker exact ou fragment du nom
   (insensible à la casse et aux accents), 20 résultats au plus. Les valeurs suivies sortent en
   premier, puis par ordre alphabétique.

### 3. Suivi et historique

7. **Suivre une valeur** marque l'instrument (`tracked_since`). L'opération est idempotente.
   Elle ne déclenche **aucun appel externe** dans la requête : l'historique est chargé par un job
   (critère 8). Les benchmarks configurés (`investai.market.benchmarks`, par défaut CAC
   `FR0007052782`, CW8 `LU1681043599` et WPEA `IE0002XZSHO1`, sur `XPAR`) sont suivis
   automatiquement dès qu'ils existent dans le référentiel.
8. **Chargement de l'historique** : pour chaque valeur suivie sans historique, 1 appel EODHD
   `eod` récupère 1 an de clôtures brutes (`close`, jamais `adjusted_close`), avec la source
   `EODHD`, puis passe `history_loaded` à vrai. Si le budget du jour est épuisé, la valeur
   attend le passage suivant du job.

### 4. Collecte quotidienne (Euronext MiFIR)

9. **Job horaire, de 9 h à 18 h (heure de Paris), tous les jours.**
   - Il calcule la séance attendue (séance précédente au calendrier).
   - Si les deux segments sont déjà collectés pour cette séance, il ne télécharge rien.
   - Sinon, il télécharge `EQUITIES` et `ETFS_FUNDS` de `PAR` (`PREVIOUS_TRADING_DAY`).
   - Si la séance contenue dans le fichier n'est pas la séance attendue (fichier pas encore
     régénéré), il n'écrit rien et réessaie à l'heure suivante.
10. **Calcul de la clôture**, uniquement pour les valeurs suivies : dernière transaction
    `MmtMarketMechanism = 1` dont `Venue` est le MIC de l'instrument.
    - L'ordre est celui de `TradingDateTime`. En cas d'égalité d'horodatage avec des prix
      différents, l'ordre du fichier départage, et un avertissement est journalisé.
    - Une valeur sans transaction ce jour-là n'a **pas de ligne** (aucun prix inventé).
    - Si une valeur suivie n'a des transactions que sur un autre lieu de Paris, un avertissement
      est journalisé (signe d'une règle MIC fausse).
11. **Lecture robuste du fichier** :
    - lecture **en flux** depuis la réponse HTTP (ni fichier temporaire, ni chargement complet
      en mémoire) ;
    - une seule entrée CSV attendue dans le ZIP ; volume décompressé plafonné (500 Mo
      configurables), au-delà la collecte échoue proprement ;
    - première ligne (copyright) ignorée ; colonnes repérées par leur nom ; échec explicite si
      une colonne requise manque ;
    - une ligne mal formée est ignorée et comptée, pas d'échec global ;
    - prix lus en `BigDecimal` depuis le texte, ramenés à 4 décimales sans arrondi (au-delà,
      la ligne est rejetée) ; horodatages UTC.
12. **Idempotence et priorité des sources** : réécrire une séance déjà collectée ne crée pas de
    doublon. Une clôture `EURONEXT` n'est jamais remplacée par une clôture `EODHD`. Si les deux
    sources diffèrent pour une même séance, un avertissement est journalisé avec l'instrument et
    la date, sans les prix.
13. **Rattrapage** : après une collecte réussie, les séances manquantes entre la dernière séance
    collectée et la nouvelle (application éteinte) sont demandées à EODHD.
    - 1 appel `eod` `from`/`to` par valeur suivie, quelle que soit la durée du trou.
    - Les séances rattrapées sont enregistrées dans `market_collection`, avec la source `EODHD`.
14. **Appels sortants identifiables** : `User-Agent` `InvestAI/<version> (personal use;
    +https://github.com/theophanetsy/InvestAI)`. Un seul téléchargement réussi par fichier et
    par séance (zone grise acceptée dans l'ADR-0009).

### 5. EODHD : clé et budget

15. **Clé** dans `INVESTAI_EODHD_API_KEY`.
    - Absente : l'application démarre quand même, la collecte Euronext fonctionne, et les
      fonctions EODHD sont désactivées avec un seul avertissement au démarrage.
    - La clé n'apparaît dans **aucun log, message d'exception, problème HTTP ni contrat
      OpenAPI**. Un test simule une erreur réseau, une erreur HTTP et une réponse illisible, puis
      vérifie que la clé est absente des messages et des journaux capturés.
16. **Budget quotidien** (`investai.market.eodhd.daily-call-budget`, 15 par défaut, sous le
    quota de 20) :
    - compteur en base, réservé **avant** chaque appel ; un appel refusé faute de budget est
      reporté, pas perdu ;
    - consommation journalisée à chaque appel (`calls=3/15`) ;
    - priorités dans l'ordre : rattrapage, puis historique des nouvelles valeurs suivies, puis
      référentiel.

### 6. API REST (module `market`, tag `market`, authentifiée)

17. Endpoints :
    - `GET /api/market/instruments?query=` : recherche (critère 6) ;
    - `GET /api/market/instruments/{id}` : détail de l'instrument, avec sa dernière clôture
      connue et sa date ;
    - `GET /api/market/instruments/{id}/prices?from=&to=` : clôtures de la période, par date
      croissante. Période par défaut : 1 an ; maximum : 5 ans ; `from` après `to` → 400 ;
    - `PUT /api/market/instruments/{id}/tracking` : suivre une valeur (critère 7), réponse 204.

    Un instrument inconnu renvoie un 404 avec le code `instrument.not-found`. Les prix sont des
    chaînes décimales avec leur devise, jamais des nombres flottants. Le contrat
    `api/openapi.json` et le client Angular sont régénérés, et le tag `market` est ajouté à
    `OpenApiContractIT`.

### 7. Tests et qualité

18. **Aucun appel réseau réel dans les tests**, et **aucune donnée réelle commitée** : fichiers
    ZIP/CSV et réponses JSON synthétiques au format réel, générés par le test ou écrits à la
    main. Les jobs sont désactivables par propriété, désactivés dans les tests et déclenchés
    explicitement.
19. Couverture :
    - tests unitaires : calendrier, `Isin`, lecteur MiFIR, calcul de la clôture, règles MIC et
      type, budget, masquage de la clé ;
    - tests d'intégration (`@IntegrationTest`, vraie base) : migration, idempotence, priorité des
      sources, rattrapage, API.
    `./mvnw verify` est vert, sans avertissement ni exclusion ajoutée.
20. **Sans nouvelle dépendance** : HTTP par le `RestClient` de Spring, ZIP par `java.util.zip`,
    CSV lu à la main (format simple, sans virgule dans les champs), serveur HTTP de test du JDK
    ou `MockRestServiceServer`.
    **Pas de ShedLock dans cette étape** : une seule instance tourne, et l'idempotence est
    garantie par les contraintes d'unicité. ShedLock arrivera avec le déploiement
    multi-instances (Phase 10) ; la ROADMAP et l'ADR-0009 sont mis à jour en conséquence.

### 8. Capitaliser et livrer

21. Documentation :
    - `docs/setup.md` : `INVESTAI_EODHD_API_KEY` (où la créer, dans `infra/.env`) et
      fonctionnement sans clé ;
    - `backend/CLAUDE.md` : conventions du module `market`, et pièges rencontrés (par exemple un
      message d'exception HTTP qui contient l'URL, donc la clé) ;
    - `frontend/CLAUDE.md` : contrat des nouveaux endpoints ;
    - ROADMAP à jour.
22. Livraison :
    - revue par les agents `java-reviewer` et `security-reviewer` (intégration externe) ;
    - vérification réelle par le propriétaire, avec sa clé : un démarrage charge le référentiel
      et l'historique des benchmarks, puis la collecte du lendemain matin ajoute la clôture de la
      veille ;
    - PR avec la section « Pour les prochains agents ».

## Hors périmètre

- **UI** : la recherche de valeurs arrive dans l'UI en 2.2 (saisie des transactions), les cours
  en 2.5 (dashboard).
- **Opérations sur titres et dividendes** (appels EODHD `div` et `splits`) : en 2.3, avec la
  valorisation et les attributions gratuites. D'ici là, les clôtures sont brutes, non ajustées.
- **Autres places Euronext** (Amsterdam…) et Euronext Access (`XMLI`) : le modèle `(isin, mic)`
  les permet, mais leur règle MIC et leurs fichiers ne sont pas traités.
- Cours en séance (`real-time`), taux de change, import manuel des exports Euronext au-delà
  d'1 an.
- Valeurs sorties de la cote : elles restent en base, sans marquage particulier.

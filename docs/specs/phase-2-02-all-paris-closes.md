# Phase 2.2 — Clôtures de toutes les valeurs de Paris

## Contexte

La [Phase 2.1](phase-2-01-market-data-collection.md) télécharge chaque jour les deux fichiers
différés d'Euronext Paris (MiFIR, [ADR-0009](../adr/0009-fournisseur-donnees-marche.md)) mais ne
garde la clôture que des **valeurs suivies** (benchmarks, et plus tard portefeuille déclaré).
Depuis le recentrage du 2026-10-19 ([ROADMAP](../ROADMAP.md), Phase 2), l'application devient un
outil de conseil : l'agent de la Phase 3 devra pouvoir étudier **toutes** les valeurs de Paris,
pas seulement celles que le propriétaire a déjà repérées.

Les fichiers contiennent déjà toutes les transactions de la place : garder la clôture de chaque
valeur du référentiel ne coûte **aucun appel supplémentaire**. Cette étape étend donc la collecte
quotidienne à tout le référentiel (~1 300 actions `XPAR` / `ALXP` et ETF), et garde au passage
le **volume** et le **nombre de transactions** de la séance, utiles plus tard pour juger la
liquidité (petites valeurs).

Le sens de « suivre une valeur » ne change pas : seules les valeurs suivies reçoivent 1 an
d'historique EODHD et le rattrapage des jours manqués (budget de 15 appels par jour inchangé).

Spec technique, validée par délégation du propriétaire (voir « Hypothèses »).

## Critères d'acceptation

### 1. Collecte de tout le référentiel

1. **Clôture de chaque valeur du référentiel** : à chaque fichier Euronext collecté (`EQUITIES`
   et `ETFS_FUNDS` de Paris), une clôture `EURONEXT` est enregistrée pour **chaque instrument du
   référentiel** (suivi ou non) qui a au moins une transaction retenue ce jour-là. Règle de
   clôture inchangée (spec 2.1, critère 10) : dernière transaction `MmtMarketMechanism = 1` sur le
   MIC de l'instrument, dans sa devise, par ordre de `TradingDateTime` puis ordre du fichier.
   Une valeur sans transaction retenue n'a pas de ligne (aucun prix inventé).
2. **ISIN hors référentiel** : les transactions d'un ISIN absent du référentiel sont ignorées ;
   leur nombre et le nombre d'ISIN distincts concernés figurent dans le log de fin de fichier
   (`unknownIsins=…, unknownTrades=…`).
3. **Anomalies agrégées** : avec ~1 300 valeurs, un avertissement par valeur noierait les logs.
   Pour chaque type d'anomalie de la spec 2.1 (horodatages égaux à prix différents, échanges
   seulement sur un autre lieu, clôture à plus de 4 décimales, autre devise), **une seule ligne**
   par fichier : le nombre de cotations concernées et au plus 10 d'entre elles (ISIN + MIC, jamais
   de prix).
4. `market_collection.closes_count` compte les clôtures de tout le référentiel enregistrées pour
   le fichier.

### 2. Volume et nombre de transactions

5. **Nouvelle migration Flyway** `V3__daily_price_activity.sql` (V2 n'est pas modifiée) :
   `daily_price.volume` (`bigint`, nullable, `>= 0`) et `daily_price.trade_count` (`integer`,
   nullable, `>= 0`), documentées par `COMMENT ON COLUMN`. Les lignes existantes gardent des
   valeurs nulles.
6. **Euronext** : pour une clôture `EURONEXT`, `trade_count` = nombre de transactions retenues
   pour la clôture (mécanisme 1, lieu de cotation, devise de l'instrument, séance attendue) et
   `volume` = somme de leurs quantités (`MifidQuantity`, en titres). Si une de ces quantités est
   illisible, négative ou non entière, ou si la somme dépasse un `bigint`, le volume est **nul**
   (inconnu) ; la clôture et le nombre de transactions restent enregistrés.
7. **EODHD** : pour une clôture `EODHD` (historique, rattrapage), `volume` = champ `volume` de la
   barre quand c'est un entier positif ou nul, sinon nul ; `trade_count` est toujours nul (non
   fourni). Un volume EODHD absent ou invalide ne fait jamais échouer l'appel.
8. **Priorité des sources inchangée** : une clôture `EURONEXT` n'est jamais remplacée par une
   clôture `EODHD`, volume et nombre de transactions compris ; une clôture `EURONEXT` remplace une
   clôture `EODHD` avec ses propres volume et nombre de transactions. Réécrire une séance reste
   idempotent.

### 3. Suivi, historique et rattrapage (inchangés)

9. Seules les valeurs **suivies** reçoivent 1 an d'historique EODHD et entrent dans la file de
   rattrapage ; le budget EODHD (15 appels/jour) et ses priorités ne changent pas. Les autres
   valeurs n'ont que les séances collectées depuis la mise en service, **avec des trous** les
   jours où la collecte n'a pas eu lieu (application éteinte au-delà du jour de publication du
   fichier). Limite assumée, documentée dans `backend/CLAUDE.md`.
10. Suivre une valeur qui a déjà des clôtures `EURONEXT` charge quand même son année
    d'historique ; les clôtures `EURONEXT` déjà en base sont conservées (critère 8).

### 4. Garde-fous et performance

11. **Aucune régression** sur la lecture en flux (ni fichier temporaire, ni chargement complet),
    le plafond de taille décompressée, la longueur maximale de ligne, le rejet compté des lignes
    mal formées, l'idempotence et la priorité Euronext > EODHD : les tests existants restent
    verts sans être affaiblis. La colonne `MifidQuantity` devient requise dans l'en-tête (échec
    explicite si elle manque), mais une quantité illisible ne rejette pas la ligne (critère 6).
12. **Mesure de performance** sur un fichier synthétique de taille réaliste (~483 000
    transactions, ~85 Mo décompressés, ~1 300 valeurs du référentiel et des ISIN hors
    référentiel) : test d'intégration sur la vraie base qui lit le fichier, calcule les clôtures
    et les écrit (upsert par lots), vérifie le résultat et journalise les durées. Garde-fou large
    contre une régression grossière (< 120 s pour l'ensemble). Les durées mesurées sont
    consignées dans la section « Écarts et précisions » de cette spec et dans `backend/CLAUDE.md`.

### 5. API

13. `GET /api/v1/market/instruments/{id}/prices` (et `lastClose` du détail, même schéma
    `DailyCloseResponse`) renvoie deux champs **optionnels** : `volume` (entier, titres échangés)
    et `tradeCount` (entier), absents quand ils sont inconnus. Le détail et les prix fonctionnent
    pour tout instrument du référentiel, suivi ou non. Contrat `api/openapi.json` et client Angular
    régénérés (`npm run api:update`).

### 6. Tests, qualité, documentation

14. Tests unitaires : calculateur (clôtures de tout le référentiel, ISIN inconnus, volume, nombre
    de transactions, volume inconnu), lecteur MiFIR (quantité lue, quantité illisible, colonne
    manquante), volume EODHD, journal des anomalies agrégé. Tests d'intégration : migration,
    écriture et relecture du volume, priorité des sources avec volume, collecte d'une valeur non
    suivie, API. Données synthétiques uniquement, aucun appel réseau.
15. `npm run check` vert (intégration comprise), Semgrep local vert ; sans nouvelle dépendance,
    sans exclusion ni seuil abaissé.
16. Documentation : `backend/CLAUDE.md` (module `market` : ce qui change, limites, pièges),
    `frontend/CLAUDE.md` (champs `volume` / `tradeCount`), ROADMAP (2.2, et éligibilité PEA
    déplacée en 2.3).

## Hors périmètre

- **Éligibilité PEA** des valeurs : déplacée en **2.3**, la règle sera validée par le
  propriétaire.
- **UI** (recherche, cours), **indicateurs** (2.4) dont tout calcul de liquidité à partir du
  volume.
- **Autres places Euronext** (Amsterdam…), Euronext Access (`XMLI`).
- Combler les trous des valeurs non suivies (EODHD : 1 appel par valeur, hors budget).
- Volume ou nombre de transactions hors carnet central (mécanisme ≠ 1) ou sur un autre lieu.

## Hypothèses

Prises par l'agent, la validation de cette spec technique ayant été déléguée par le propriétaire.

| # | Hypothèse | Raison / alternative écartée |
|---|---|---|
| H1 | « Référentiel » = **tous les instruments en base** (table `instrument`), quel que soit leur type (`STOCK`, `ETF`, `FUND`, `OTHER`), y compris ceux absents du dernier référentiel EODHD (conservés par la 2.1). | Le référentiel ne contient que Paris ; filtrer par type écarterait sans raison des valeurs que le fichier couvre. |
| H2 | Volume Euronext = titres échangés en **carnet central (mécanisme 1) sur le lieu de cotation, dans la devise de l'instrument** : exactement les transactions candidates à la clôture. | Même périmètre que la clôture ; le spike (ADR-0009) a montré que nombre de transactions et titres échangés ainsi filtrés correspondent à l'export officiel. |
| H3 | Le volume est un **nombre entier de titres** (`bigint`) ; une quantité fractionnaire rend le volume de la séance inconnu plutôt que d'arrondir. | Actions et ETF s'échangent par titres entiers ; un `numeric` compliquerait l'API pour un cas non observé. |
| H4 | Le volume EODHD n'a **pas la même définition** que celui d'Euronext (tous mécanismes possibles, non documenté) : la colonne `source` dit d'où il vient. Les deux ne sont pas comparés. | Pas de donnée pour vérifier ; l'usage prévu (ordre de grandeur de la liquidité) le tolère. |
| H5 | Anomalies agrégées par type avec 10 exemples au plus (critère 3), au lieu d'une ligne par valeur. | Lisibilité des logs avec ~1 300 valeurs ; les exemples suffisent pour enquêter. |
| H6 | Aucun rattrapage EODHD pour les valeurs non suivies, ni de marquage des trous. | Budget de 15 appels/jour ; limite assumée par l'énoncé de l'étape. |

## Écarts et précisions d'implémentation

À compléter pendant l'implémentation.

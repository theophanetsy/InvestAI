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
   - Ajustée des divisions et attributions gratuites (critère 5).
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

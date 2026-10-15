# 0008 — Bibliothèque UI : Angular Material

- **Statut** : Accepté
- **Date** : 2026-10-15

## Contexte

Le shell Angular (Phase 1.5) puis les features (dashboard, chat, actu, simulation) ont besoin de
composants accessibles : barre d'application, navigation latérale, formulaires, dialogues,
notifications, tableaux. Les écrire à la main coûte du code et des tests, et l'accessibilité
(focus, clavier, ARIA) est facile à rater. Contraintes : Angular 22 zoneless et signals, règles
d'accessibilité ESLint actives, peu de dépendances (CLAUDE.md), pas de requête vers un CDN tiers
depuis une app qui manipule des données patrimoniales.

## Options étudiées

### Angular Material (+ CDK)
- ➕ Maintenue par l'équipe Angular : même numéro de version, mises à jour synchronisées
  (`ng update`), compatible zoneless et signals.
- ➕ Accessibilité soignée et documentée ; CDK (overlay, breakpoints, a11y) réutilisable seul.
- ➕ Thème Material 3 en variables CSS, clair/sombre selon le système, sans dépendance de plus.
- ➖ Catalogue sobre : tableau simple, pas de graphiques (une lib de graphiques sera choisie en
  Phase 2 de toute façon).

### PrimeNG
- ➕ Catalogue très riche : tableaux avancés (tri, filtres, export), graphiques via Chart.js.
- ➖ Lib tierce : surface et dépendances plus grandes, versions et accessibilité moins alignées
  sur Angular.

### Aucune (CSS maison + CDK)
- ➕ Le plus léger, formateur pour le CSS.
- ➖ Chaque composant accessible (menu, dialogue, champ de formulaire) à écrire et tester.

## Décision

**Angular Material** et **@angular/cdk**, à la version d'Angular (22.x), mis à jour ensemble.

- Thème M3 unique dans `src/styles.scss` (`mat.theme`), `theme-type: color-scheme` (suit le
  système) ; personnalisation par variables `--mat-sys-*`, jamais en surchargeant les classes
  internes `.mat-mdc-*`.
- **Polices système**, pas de Roboto ni de Material Symbols chargés depuis Google Fonts. Pas
  d'icônes pour l'instant (boutons texte) ; si besoin, icônes SVG locales.
- Pas de `@angular/animations` : Material 22 anime en CSS natif.

## Conséquences

- Positives : composants accessibles prêts à l'emploi, un seul rythme de mise à jour (Dependabot
  groupe Angular).
- Négatives / dette : apparence « Material » reconnaissable ; bundle initial plus lourd (surveillé
  par les budgets `angular.json`). Les tableaux avancés et graphiques du dashboard demanderont une
  décision séparée (Phase 2).
- Suivi : réévaluer si un besoin de composant riche (grille de données, graphiques) n'est pas
  couvert sans effort disproportionné.

# Installation du poste de dev

## Prérequis

| Outil | Version | Installation (Windows) |
|---|---|---|
| JDK Temurin | 25 | `winget install EclipseAdoptium.Temurin.25.JDK` puis définir `JAVA_HOME` |
| Node.js | 24 (`.nvmrc`) | https://nodejs.org ou `nvm` |
| gitleaks | ≥ 8.30 | `winget install Gitleaks.Gitleaks` (requis par le hook pre-commit) |
| Docker Desktop | récent | **obligatoire** : PostgreSQL local et tests d'intégration (Testcontainers) |
| GitHub CLI | récent | `winget install GitHub.cli` |

Maven n'a pas besoin d'être installé : utiliser `backend/mvnw`.

## Premier lancement

```bash
npm install                 # installe Lefthook + commitlint et active les git hooks
cd frontend && npm ci && npx playwright install chromium
cd ../backend && ./mvnw verify   # Docker doit tourner (Testcontainers)
```

## Lancer l'application en local

```bash
cp infra/.env.example infra/.env     # puis renseigner les variables (fichier ignoré par Git)
node scripts/hash-password.mjs      # → valeur de INVESTAI_PASSWORD_HASH dans infra/.env
cd backend && ./mvnw spring-boot:run # profil local : démarre PostgreSQL via Docker Compose
cd frontend && npm start             # http://localhost:4200 (proxy /api → :8080)
```

- `infra/.env` contient `POSTGRES_PASSWORD`, `INVESTAI_USERNAME` et `INVESTAI_PASSWORD_HASH`
  (hash PBKDF2, jamais le mot de passe en clair ; [ADR-0007](adr/0007-authentification-mono-utilisateur.md)).
  Sans eux, le backend refuse de démarrer. Il contient aussi `INVESTAI_EODHD_API_KEY`, facultative
  (données de marché, voir plus bas), et `INVESTAI_MCP_TOKEN_HASH`, facultative (serveur MCP pour
  Claude, voir plus bas).
- Ouvrir http://localhost:4200 : l'écran de connexion demande `INVESTAI_USERNAME` et le mot de
  passe dont le hash est dans `INVESTAI_PASSWORD_HASH`.
- Santé : http://localhost:8080/actuator/health/readiness (détail de la base une fois connecté).

### Données de marché (EODHD, Euronext)

Le backend collecte seul les données de marché ([ADR-0009](adr/0009-fournisseur-donnees-marche.md),
[spec 2.1](specs/phase-2-01-market-data-collection.md)) :

- **Clôtures quotidiennes** : fichiers différés gratuits d'Euronext, sans clé. Job toutes les
  heures de 9 h à 23 h (heure de Paris) et 1 min après le démarrage, séance précédente, un seul
  téléchargement réussi par
  fichier et par séance.
- **Référentiel, 1 an d'historique, rattrapage des jours manqués** : offre gratuite d'EODHD.
  Créer un compte sur https://eodhd.com/register, copier la clé d'API du tableau de bord et
  l'ajouter à `infra/.env` : `INVESTAI_EODHD_API_KEY=<clé>` (jamais ailleurs, jamais commitée).
  Job au démarrage (après 30 s) puis toutes les heures ; budget de 15 appels par jour UTC
  (quota gratuit : 20), consommation visible dans les logs (`calls=3/15`).
- **Sans clé** : l'application démarre, la collecte Euronext tourne, les fonctions EODHD sont
  désactivées (un seul avertissement au démarrage). Sans référentiel, aucune valeur n'est suivie :
  la clé est nécessaire pour un usage réel.
- Premier démarrage avec la clé : référentiel de Paris (1 appel), suivi automatique des
  benchmarks (CAC, CW8, WPEA) et de leur historique (3 appels). Au démarrage suivant (ou à la
  prochaine heure pleine entre 9 h et 23 h), la collecte ajoute la clôture de la séance précédente.
- Couper les jobs (dépannage) : `INVESTAI_MARKET_JOBS_ENABLED=false`. La stack de vérification
  (`npm run stack:start`) les coupe toujours.
- Arrêter la base : `docker compose -f infra/docker-compose.yml stop` (Spring ne l'arrête pas,
  `lifecycle-management=start-only`). Repartir de zéro : `... down -v` (supprime les données).

### Brancher Claude sur InvestAI (serveur MCP, Phase 3)

Le conseil se fait dans Claude Code ou Claude Desktop (abonnement), qui lisent les données
d'InvestAI par le serveur MCP du backend ([ADR-0010](adr/0010-ia-via-mcp-uniquement.md),
[spec 3.0](specs/phase-3-00-mcp-server.md)) : `http://127.0.0.1:8080/mcp`, Streamable HTTP sans
état, jeton porteur dédié. L'application n'appelle jamais l'API Anthropic. Le backend doit tourner
pendant la session de conseil.

1. **Générer le jeton** (une fois) :

   ```bash
   node scripts/mcp-token.mjs     # → INVESTAI_MCP_TOKEN_HASH=<empreinte>
   ```

   Le jeton n'est jamais affiché : il est écrit dans `~/.investai/mcp-token` (hors du dépôt,
   jamais écrasé sans `--force`). Copier la ligne affichée dans `infra/.env`, puis redémarrer le
   backend. Le backend ne connaît que l'empreinte SHA-256.
   - Sans `INVESTAI_MCP_TOKEN_HASH`, `/mcp` n'est pas monté (avertissement au démarrage
     « MCP server disabled ») ; le reste de l'application fonctionne.
   - Une empreinte mal formée (ou le jeton collé à sa place) empêche le démarrage.
   - Changer de jeton : `node scripts/mcp-token.mjs --force`, remplacer la ligne de `infra/.env`,
     redémarrer, puis rebrancher les clients.
2. **Vérifier** (Git Bash ; l'en-tête est passé par un fichier temporaire du shell, pour que le
   jeton n'apparaisse pas dans les arguments d'un processus) :

   ```bash
   curl -s http://127.0.0.1:8080/mcp      -H @<(printf 'Authorization: Bearer %s' "$(cat ~/.investai/mcp-token)")      -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream'      -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"getHealth","arguments":{}}}'
   ```

   Réponse attendue : `structuredContent` avec `status`, `version`, `closesAsOf`,
   `indicatorsAsOf`, `indicatorsComputedAt`. Sans jeton : 401 ; avec un en-tête `Origin` : 403 ;
   plus de 120 requêtes par minute : 429.
3. **Claude Code**, en portée **locale d'un dossier dédié hors du dépôt** :

   ```bash
   mkdir -p ~/investai-conseil && cd ~/investai-conseil
   cat > investai-mcp.json <<'EOF'
   {"type":"http","url":"http://127.0.0.1:8080/mcp","headersHelper":"printf '{\"Authorization\":\"Bearer %s\"}' \"$(cat ~/.investai/mcp-token)\""}
   EOF
   claude mcp add-json --scope local investai "$(cat investai-mcp.json)" && rm investai-mcp.json
   ```

   Puis lancer `claude` depuis `~/investai-conseil` pour les sessions de conseil.
   - **Pourquoi pas la portée utilisateur** : `--scope user` rend le serveur disponible dans
     **tous** les projets, y compris les sessions ouvertes dans `C:/dev/InvestAI` et leurs
     sous-agents : un agent de code manipulé par un contenu piégé pourrait lire les données
     patrimoniales réelles ([modèle de menace](security/ai-threat-model.md), LLM02). La portée
     `local` ne charge le serveur que dans le dossier où la commande a été lancée.
   - **Jamais dans le `.mcp.json` du dépôt** (ni `--scope project`), pour la même raison.
   - Le `headersHelper` lit le jeton à chaque connexion : il n'est ni copié dans `~/.claude.json`
     ni passé en argument. Plus simple mais moins discret :
     `--header "Authorization: Bearer $(cat ~/.investai/mcp-token)"` (jeton rangé en clair dans
     `~/.claude.json`).
   - `/mcp` dans Claude Code affiche l'état du serveur ; l'outil `getHealth` doit apparaître.
4. **Claude Desktop** : il ne lance que des serveurs locaux en stdio, d'où la passerelle sans
   dépendance `scripts/mcp-stdio-gateway.mjs`. Dans `claude_desktop_config.json` (Paramètres →
   Développeur → Modifier la configuration ; `%APPDATA%\Claude\` sous Windows) :

   ```json
   {
     "mcpServers": {
       "investai": {
         "command": "node",
         "args": ["C:/dev/InvestAI/scripts/mcp-stdio-gateway.mjs"]
       }
     }
   }
   ```

   - La passerelle lit le jeton dans `~/.investai/mcp-token` ; autre emplacement :
     `"env": { "INVESTAI_MCP_TOKEN_FILE": "C:/…/mcp-token" }`. **Jamais le jeton dans `args`.**
   - Autre URL (stack de vérification par exemple) : `"INVESTAI_MCP_URL"` dans `env`, en HTTP sur
     `127.0.0.1`, `localhost` ou `[::1]` uniquement.
   - Si Claude Desktop ne trouve pas `node`, mettre le chemin complet
     (`C:/Program Files/nodejs/node.exe`). Diagnostics de la passerelle (préfixe
     `[investai-mcp]`, jamais le jeton) dans les journaux MCP de Claude Desktop.
5. **Règles d'usage** (ADR-0010) :
   - analyser (surtout des news, Phase 4) dans une conversation ou un projet Claude où **seul le
     connecteur InvestAI est activé** : une donnée piégée pourrait pousser le modèle à recopier
     le portefeuille via un autre connecteur ([modèle de menace](security/ai-threat-model.md)) ;
   - ne jamais choisir « toujours autoriser » pour un outil d'écriture (Phase 5) ;
   - vérifier le réglage de confidentialité du compte Claude (utilisation des conversations pour
     l'entraînement) : le profil et le portefeuille partent chez Anthropic par le client.

## Vérifier l'application réelle (agent `app-verifier`)

```bash
npm run stack:start    # base jetable + backend :18080 + frontend http://localhost:14200
npm run stack:status   # rappelle l'URL et l'identifiant
npm run stack:stop     # arrête tout, supprime la base jetable et les snapshots du navigateur
```

- `/mcp` y est monté avec un **jeton jetable** (champ `mcpToken` de `.local-stack/state.json`,
  jamais affiché ; l'empreinte de `infra/.env` est ignorée) : `http://127.0.0.1:18080/mcp`, ou la
  passerelle avec `INVESTAI_MCP_URL=http://127.0.0.1:18080/mcp`.
- Copie **isolée** de l'app (projet Compose `investai-verify`, ports 15432 / 18080 / 14200) :
  elle peut tourner en même temps que la stack de développement, sans toucher à ses données ni
  à `infra/.env`. Identifiant `verifier` affiché au démarrage ; mot de passe aléatoire
  jamais affiché, à lire dans le champ `password` de `.local-stack/state.json` (ignoré par Git,
  avec les journaux).
- Dans Claude Code : `/verify-app <critères>` lance l'agent `app-verifier`, qui pilote un
  navigateur via le serveur MCP `investai-browser` (`.mcp.json` : Playwright MCP en version
  figée, sans interface, requêtes restreintes à `http://localhost:14200` : un garde-fou, pas une
  frontière de sécurité). Claude Code demande d'approuver ce serveur au premier lancement.
  Navigateur utilisé : Chrome installé (sinon `cd frontend && npx playwright install chrome`).
  Monter la version dans `.mcp.json` à la main (Dependabot ne la suit pas).

## Configuration GitHub

Déjà en place (dépôt public) :

- Ruleset **`protect-main`** (Settings → Rules → Rulesets) : PR obligatoire, historique linéaire,
  pas de force-push ni de suppression, merge squash/rebase, checks requis : `Backend verify`,
  `Frontend check`, `Frontend e2e`, `Secret scan (gitleaks)`, `CodeQL (*)`, `SAST (Semgrep)`,
  `Dependency vulnerabilities (OSV-Scanner)`, `Filesystem & IaC scan (Trivy)`,
  `Dependency review`, `Conventional PR title`, `Conventional commits`.
- **Secret scanning + push protection**, **Dependabot alerts + security updates**.

Reste à faire manuellement :

1. **SonarCloud** : créer l'organisation `theophanetsy` et le projet `theophanetsy_InvestAI` sur
   https://sonarcloud.io (import GitHub), désactiver l'« Automatic Analysis », puis ajouter le
   secret `SONAR_TOKEN` (Settings → Secrets and variables → Actions). Ajouter ensuite
   `SonarCloud quality gate` aux checks requis.

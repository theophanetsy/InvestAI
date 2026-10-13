# InvestAI

Assistant personnel de gestion de PEA / compte-titres : dashboard de suivi, agent IA de conseil,
veille boursière automatisée et simulations de portefeuilles fictifs.

> Projet d'apprentissage : Spring Boot / Angular et intégration d'IA en production
> (tools, MCP, observabilité, evals). Ne constitue pas un conseil en investissement.

## Démarrage

Voir [docs/setup.md](docs/setup.md), puis :

```bash
npm install                                # git hooks (Lefthook, commitlint)
cd backend && ./mvnw spring-boot:run       # http://localhost:8080/api/v1/status
cd frontend && npm ci && npm start         # http://localhost:4200
```

## Documentation

- [Roadmap](docs/ROADMAP.md)
- [Décisions d'architecture (ADR)](docs/adr/README.md)
- [Consignes pour les coding agents](CLAUDE.md)
- [Risques de sécurité acceptés](docs/security/accepted-risks.md)

## Qualité

`npm run check` à la racine exécute l'ensemble des contrôles locaux (équivalent CI).
Le détail des garde-fous est dans l'[ADR 0003](docs/adr/0003-garde-fous-qualite.md).

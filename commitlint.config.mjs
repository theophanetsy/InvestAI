export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'scope-enum': [
      2,
      'always',
      ['backend', 'frontend', 'infra', 'ci', 'docs', 'deps', 'agent', 'portfolio', 'market', 'assistant', 'news', 'simulation', 'security', 'repo'],
    ],
    'subject-case': [0],
    'body-max-line-length': [1, 'always', 100],
  },
};

// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

// Lint rules are part of the quality gate: do not disable or downgrade them inline.
module.exports = defineConfig([
  {
    ignores: ['dist/', 'coverage/', '.angular/', 'src/app/api/generated/', 'playwright-report/'],
  },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.strictTypeChecked,
      tseslint.configs.stylisticTypeChecked,
      angular.configs.tsAll,
    ],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: __dirname,
      },
    },
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'inv', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'inv', style: 'kebab-case' },
      ],
      // Standalone components use a single file name suffix-less convention (Angular 20+ style guide).
      '@angular-eslint/component-class-suffix': 'off',
      // i18n will be handled later through a dedicated ADR.
      '@angular-eslint/require-localize-metadata': 'off',
      '@angular-eslint/use-component-selector': 'error',
      // Same severity as former tsc noUnusedLocals/noUnusedParameters (`_` prefix = intentionally unused);
      // generated client is ignored (ADR-0004).
      '@typescript-eslint/no-unused-vars': ['error', { args: 'all', argsIgnorePattern: '^_' }],
      '@typescript-eslint/no-unused-private-class-members': 'error',
      // Angular components may be fully declared by their decorator (e.g. template-only pages).
      '@typescript-eslint/no-extraneous-class': ['error', { allowWithDecorator: true }],
      '@typescript-eslint/explicit-function-return-type': [
        'error',
        { allowExpressions: true, allowTypedFunctionExpressions: true },
      ],
      '@typescript-eslint/explicit-member-accessibility': ['error', { accessibility: 'no-public' }],
      '@typescript-eslint/consistent-type-imports': 'error',
      '@typescript-eslint/no-floating-promises': 'error',
      'no-console': 'error',
      'no-debugger': 'error',
      'no-eval': 'error',
      'no-implied-eval': 'error',
      eqeqeq: ['error', 'always'],
      complexity: ['error', 10],
      'max-lines': ['error', { max: 300, skipBlankLines: true, skipComments: true }],
      'max-depth': ['error', 3],
      'max-params': ['error', 4],
    },
  },
  {
    files: ['**/*.spec.ts', 'e2e/**/*.ts'],
    rules: {
      'max-lines': 'off',
      '@typescript-eslint/no-non-null-assertion': 'off',
    },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateAll],
    rules: {
      '@angular-eslint/template/i18n': 'off',
      // OnPush + signals make template function calls cheap and idiomatic.
      '@angular-eslint/template/no-call-expression': 'off',
      '@angular-eslint/template/prefer-ngsrc': 'error',
    },
  },
]);

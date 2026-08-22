# Repository Guidelines

## Project Structure & Module Organization
- `plugin/`: Main Bukkit/Spigot/Paper/Folia plugin (Java 17), including web API handlers and bundled static web assets under `src/main/resources`.
- `plugin-proxy/`: Proxy-side plugin for Waterfall/Bungee/Velocity.
- `frontend/glassx/`: Vue 3 + TypeScript + Vite admin/user web UI.
- `docs/` and `docs/zh/`: screenshots and documentation assets.
- Root metadata: `version.yml` (version source and bilingual release notes), and GitHub workflows in `.github/workflows/`.

## Build, Test, and Development Commands
- Frontend dev server:
  ```bash
  cd frontend/glassx && npm ci && npm run dev
  ```
- Frontend production build:
  ```bash
  cd frontend/glassx && npm run build
  ```
- Frontend quality checks:
  ```bash
  cd frontend/glassx && npm run lint && npm run type-check && npm run test
  ```
- Build main plugin:
  ```bash
  cd plugin && mvn clean package
  ```
- Build proxy plugin:
  ```bash
  cd plugin-proxy && mvn clean package
  ```
- Full release flow mirrors CI: build `frontend/glassx`, copy `dist/*` into `plugin/src/main/resources/static/glassx/`, then package both Maven modules.

## Coding Style & Naming Conventions
- Java: 4-space indentation, `UpperCamelCase` classes, `lowerCamelCase` methods/fields, package root `team.kitemc.verifymc`.
- Vue/TypeScript: follow ESLint + Prettier (`frontend/glassx/.eslintrc.cjs`); component files use `UpperCamelCase` (for example, `UserManagement.vue`), composables use `useXxx.ts`.
- Keep i18n keys synchronized across `messages_en.properties`, `messages_zh.properties`, and frontend locale JSON files.

## Testing Guidelines
- Frontend uses Vitest (`npm run test`), with specs colocated as `*.spec.ts` (example: `src/composables/useAdminUsers.spec.ts`).
- Backend uses JUnit 5 via Maven Surefire (`mvn test`) in each Java module.
- Add or update tests for behavior changes in API handlers, services, composables, and validation logic.

## Commit & Pull Request Guidelines
- Use Conventional Commit style seen in history: `feat:`, `fix:`, `refactor:`, `style:`, `chore:` with optional scope (for example, `fix(proxy): ...`).
- Keep commits focused by module (`plugin`, `plugin-proxy`, or `frontend/glassx`) where possible.
- PRs should include:
  - concise summary and motivation,
  - linked issue (if available),
  - test/build commands run and outcomes,
  - UI screenshots/GIFs for frontend changes,
  - notes for config or migration impact (`config.yml`, DB/storage changes, version bumps).

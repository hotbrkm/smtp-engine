# AGENTS.md

## Commit Convention

This project follows [Conventional Commits](https://www.conventionalcommits.org/).

```
<type>(<scope>): <subject>
```

### Types

- `feat` - New feature
- `fix` - Bug fix
- `refactor` - Code restructuring without behavior change
- `docs` - Documentation only
- `test` - Adding or updating tests
- `chore` - Build, config, or tooling changes
- `perf` - Performance improvement
- `ci` - CI/CD changes

### Rules

- Write commit messages in **English**
- Use `!` after type/scope for breaking changes (e.g., `feat!: remove X`)
- Keep subject line under 72 characters
- Use imperative mood (e.g., "add feature" not "added feature")
- Scope is optional but encouraged (e.g., `refactor(agent): simplify resource gate`)

## Project Structure

- `Agent` - Email sending agent (core module)
- `Simulator` - SMTP test server for testing

## Build

```bash
./gradlew build          # Full build
./gradlew :Agent:build   # Agent module only
```

---
name: naksha-common
description: Explains Naksha's workspace, project settings, skill selection, ticket notes, and credential handling. Load when starting work in a Naksha code checkout.
---

# Project structure

See `CHECKOUT.md` in the code checkout for setup. `main/` is for repository management; do work in a dedicated sibling worktree. `.claude/skills/` exposes shared skills, `ai/` holds shared ticket notes, and `vault/` holds private files. Resolve project paths relative to the code checkout root, not this skill's directory.

# Environment

- `project`: Read `PROJECT`, then `project.md` in the code checkout. If neither is available, ask the user.
- `user`: Read `USER`, then try `whoami`. If neither is available, ask the user.
- `role`: Read `ROLE`, defaulting to `engineer`.

# Skills

- Prefix skill names with the project or user name. A name ending in `-role` defines a role; load the skill matching the selected role for its responsibilities.
- Prefer project skills such as `naksha-codestyle`, then personal skills such as `alweber-codestyle`, then other skills. If the project skill leaves something unclear, use the same order to fill the gap.

# Ticket working files

- Keep AI working files in `ai/<ticket-id>/`, using the actual ticket ID (for example `NAKSHA-33`).
- Put plans, investigation notes, reviews, and non-sensitive summaries there. The folder supplies the ticket ID; don't repeat it in filenames. Keep credentials, raw logs that may contain credentials, and sensitive profiles out of Git.

# Secrets

When credentials are needed, look in `vault/` for `{project}.credentials.md`, then `{user}.credentials.md`, then `credentials.md`. Ask the user if the required credentials are not available.

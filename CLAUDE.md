# Environment
Please work with three distinct settings:
- `project` — The name of the project. Read from environment variable `PROJECT`. If not defined, read from the file `project.md` from working directory, if not available at all, ask the user.
- `user` — The name of the user, read from environment variable `USER`. If not available, try command line `whoami`. If nothing works, ask the user.
- `role` — The role. Read from environment variable `ROLE`. If not defined, use `engineer`.

# Skills
- All skill names should have be prefixed either with the project-, or the user-name.
  - Prefer the project skills, fall back to user skills.
- All skills with a name ending with `-role` are treated as role skills.
  - Use the role skill to understand what your job is.
- Skills are to be used in the following order:
  1. Try skills with project prefix first, for example `naksha-codestyle`.
  2. If there is no valid project skill, try personal skills, for example [alweber-codestyle](skills/alweber-codestyle/SKILL.md).
  3. Only if nothing else valid found, try other skills.
- Duplicate skill handling:
  - If there are multiple skill that cover the same topic, prioritize the project skill.
  - Anything unclear in that skill should follow the personal skills.
  - Anything still unclear may utilize other skills.

# Secrets
Please search for secrets and credentials like database users/passwords aso in `vault/`.
First try a file name `{project}.credentials.md`, then for a `{user}.credentials.md`, and finally try `credentials.md`.
If needed credentials are not found, ask the user.

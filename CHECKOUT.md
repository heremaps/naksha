# Naksha checkout and worktrees

## Set up the workspace once

AI assistants: ask the user where to create the local `naksha/` directory. Reuse an existing workspace rather than cloning over it. The layout is:

```text
naksha/
├── .ai/               # shared local notes, organized by Jira ticket
├── .vault/            # shared private files and credentials; never commit them
├── main/              # primary clone; normally on v3, never used for work
├── naksha-skills/      # worktree for the shared Naksha skills branch
├── here-skills/        # optional checkout of the internal skills repository
├── .claude/skills/     # generated links to the individual skills
└── NAKSHA-123/        # example dedicated code worktree
```

From the location the user approved (skip the clone and skills checkout if they already exist):

```sh
mkdir -p naksha
cd naksha
(umask 077; mkdir -p .ai .vault)
git clone --branch v3 git@github.com:heremaps/naksha.git main
git -C main fetch origin
git -C main worktree add --track -b naksha-skills "$PWD/naksha-skills" origin/naksha-skills
```

The `naksha-skills` branch keeps the shared definitions in its top-level `skills/` directory. Update that worktree independently with `git -C naksha-skills pull --ff-only`. For optional internal skills, search your internal GitLab for the `here-skills` project and clone it as `here-skills/` alongside `main/`. Its definitions live in the top-level `skills/` directory.

From `naksha/`, run the helper from a code checkout that has adopted this layout:

```sh
sh main/setup-skills.sh
```

The helper links each skill into the shared `.claude/skills/` directory. Rerun it after adding, removing, or renaming skills, or after cloning the optional `here-skills/` repository. Changes to existing skill files are visible through the links without rerunning the helper. Skill names must be unique across both sources.

## Create a worktree for each task or PR

Use `main/` to fetch and manage worktrees. It can switch branches when needed (`git switch --no-overwrite-ignore <branch>` protects the local links), but do not edit or commit there. From `naksha/`, create a sibling worktree for the task, using its Jira key for both the branch and directory. If the branch already exists locally, omit `-b` and use the branch name as the final argument instead of `origin/v3`.

```sh
git -C main fetch origin
git -C main worktree add -b NAKSHA-123 "$PWD/NAKSHA-123" origin/v3
```

To review an existing PR, check out its head in a separate sibling worktree, not in `main/` or an existing task worktree. For example, for GitHub PR 123:

```sh
git -C main fetch origin pull/123/head
git -C main worktree add -b pr-123 "$PWD/pr-123" FETCH_HEAD
```

Open the dedicated worktree for edits, tests, commits, and PR preparation. Keep the primary clone available for other worktrees.

## Link the shared folders in each code checkout

Run the following from the root of each code checkout, including `main/` once its branch has adopted this layout. Do not run it in either skills checkout.

```sh
(
  for path in .claude ai vault; do
    case "$path" in
      .claude) target=../.claude ;;
      ai)      target=../.ai ;;
      vault)   target=../.vault ;;
    esac
    if [ ! -d "$target" ]; then
      printf 'Missing shared directory: %s\n' "$target" >&2
      exit 1
    fi
    if [ -e "$path" ] || [ -L "$path" ]; then
      if [ ! -L "$path" ] || [ "$(readlink "$path")" != "$target" ]; then
        printf 'Not replacing existing path: %s\n' "$path" >&2
        exit 1
      fi
    fi
  done

  [ -L .claude ] || ln -s ../.claude .claude        || exit 1
  [ -L ai ]      || ln -s ../.ai ai                || exit 1
  [ -L vault ]   || ln -s ../.vault vault          || exit 1
)
```

These links are local and ignored by Git. Claude finds the shared definitions at `.claude/skills/`; ticket notes go into `ai/<ticket-id>/`, and private files into `vault/`. Older branches or PRs may still track these paths. Do not overwrite or delete their files to install the links. Leave them as-is until the branch adopts this layout, and preserve any local notes or private files before moving them to the shared directories.

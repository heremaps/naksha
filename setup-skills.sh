#!/bin/sh
set -eu

# Resolve the workspace relative to this script's code worktree, not the caller.
workspace=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
cd "$workspace"

if [ ! -d naksha-skills/skills ]; then
  printf 'Missing skill source: %s/naksha-skills/skills\n' "$workspace" >&2
  exit 1
fi

for path in .claude .claude/skills; do
  if [ -L "$path" ] || { [ -e "$path" ] && [ ! -d "$path" ]; }; then
    printf 'Not replacing existing path: %s/%s\n' "$workspace" "$path" >&2
    exit 1
  fi
done
mkdir -p .claude/skills

is_managed() {
  case "$1" in
    "../../naksha-skills/skills/$2"|"../../here-skills/skills/$2") return 0 ;;
    *) return 1 ;;
  esac
}

# Build and validate the desired set before touching any existing links.
desired=$(mktemp -d .claude/.skills.XXXXXXXX)
trap 'find "$desired" -type l -exec unlink {} \; ; rmdir "$desired"' EXIT
trap 'exit 1' HUP INT TERM
for source in naksha-skills here-skills; do
  for skill in "$source"/skills/*; do
    [ -f "$skill/SKILL.md" ] || continue
    name=${skill##*/}
    link=.claude/skills/$name
    if [ -L "$desired/$name" ]; then
      printf 'Duplicate skill name: %s (%s and %s)\n' "$name" "$(readlink "$desired/$name")" "$skill" >&2
      exit 1
    fi
    if [ -L "$link" ]; then
      if ! is_managed "$(readlink "$link")" "$name"; then
        printf 'Not replacing existing link: %s\n' "$link" >&2
        exit 1
      fi
    elif [ -e "$link" ]; then
      printf 'Not replacing existing path: %s\n' "$link" >&2
      exit 1
    fi
    ln -s "../../$skill" "$desired/$name"
  done
done

# Leave anything not pointing to one of the skill sources alone.
for link in .claude/skills/*; do
  [ -L "$link" ] || continue
  name=${link##*/}
  current=$(readlink "$link")
  if is_managed "$current" "$name"; then
    if [ ! -L "$desired/$name" ] || [ "$(readlink "$desired/$name")" != "$current" ]; then
      unlink "$link"
    fi
  fi
done

for entry in "$desired"/*; do
  [ -L "$entry" ] || continue
  link=.claude/skills/${entry##*/}
  [ -L "$link" ] || ln -s "$(readlink "$entry")" "$link"
done

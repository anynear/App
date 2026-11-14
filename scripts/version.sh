#!/usr/bin/env bash
# Version of a release run (Semantic Versioning 2.0.0), printed as key=value lines for $GITHUB_OUTPUT:
#
#   Tag vX.Y.Z      version X.Y.Z, build number VERSION_CODE_BASE + 2n + 1
#   main            pre-release X.Y.Z-pre.<build>, build number VERSION_CODE_BASE + 2n; X.Y.Z is the open version: the
#                   highest release-notes/X.Y.Z.md that has no tag vX.Y.Z yet
#
# n = number of commits up to HEAD. A pre-release sorts before its version (0.9.0-pre.10030 < 0.9.0-pre.10032 < 0.9.0),
# and the tag on the same commit gets the next build number, so that it installs as an update.
#
#   GITHUB_REF=refs/heads/main VERSION_CODE_BASE=10000 scripts/version.sh
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
ref="${GITHUB_REF:?GITHUB_REF is not set}"
base="${VERSION_CODE_BASE:-0}"
die() { echo "::error::$*" >&2; exit 1; }

cd "$here"
git merge-base --is-ancestor HEAD origin/main || die "$ref is not on main."
count=$(git rev-list --count HEAD)
commit=$(git rev-parse --short=7 HEAD)

if [[ "$ref" == refs/tags/* ]]; then
  tag="${ref#refs/tags/}"
  [[ "$tag" =~ ^v([0-9]+\.[0-9]+\.[0-9]+)$ ]] || die "Releases only from main or a tag vX.Y.Z without a suffix, not from $tag."
  marketing="${BASH_REMATCH[1]}"
  name="$marketing"
  code=$((base + 2 * count + 1))
  pre=false
elif [ "$ref" = refs/heads/main ]; then
  marketing=$(find release-notes -maxdepth 1 -name '*.md' -exec basename {} .md \; | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -1 || true)
  [ -n "$marketing" ] || die "There is no release-notes/X.Y.Z.md for the next version."
  [ -z "$(git tag --list "v$marketing")" ] || die "v$marketing is already released. Add release-notes/<next version>.md first."
  code=$((base + 2 * count))
  name="$marketing-pre.$code"
  pre=true
else
  die "Releases only from main or a tag vX.Y.Z, not from $ref."
fi

echo "name=$name"
echo "tag=v$name"
echo "marketing=$marketing"
echo "code=$code"
echo "pre=$pre"
echo "commit=$commit"
echo "date=$(git log -1 --format=%cI)"

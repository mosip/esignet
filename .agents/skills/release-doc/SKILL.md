---
name: release-doc
description: >
  Generate or update a Markdown release document by analyzing the changes between a base git tag
  and a release branch. Use this skill whenever the user wants to create release notes, a changelog,
  or a release document from git history — even if they just say "generate release notes", "document
  what changed between branches", "create a changelog", "update the release doc", or "what changed
  since v1.2.3". Triggers on any mention of git tags, release branches, changelogs, or release notes.
  Produces a versioned .md file with AI-generated feature summaries, bug fixes, breaking changes,
  and a migration guide, derived from git diff analysis and commit messages.
---

# Release Document Skill

Generates a structured, AI-summarized Markdown release document by comparing a **base git tag**
against a **release branch**, using both `git diff` and commit messages for deep analysis.

---

## Inputs to collect from the user

Before starting, ensure you have:

| Input | How to get it |
|---|---|
| **Base tag** | e.g. `v1.2.3` — the previous release tag |
| **Release branch** | e.g. `release/1.3.0` or `main` — the current release |
| **Repo path** | Absolute or relative path to the git repo (default: current directory) |
| **Existing doc** *(optional)* | Path to a previous release `.md` file to use as structural reference |

If any are missing, ask the user before proceeding.

---

## Step 1 — Validate the repo and inputs

```bash
cd <repo_path>

# Confirm git repo
git rev-parse --is-inside-work-tree

# Confirm base tag exists
git tag | grep <base_tag>

# Confirm release branch exists
git branch -a | grep <release_branch>

# Show the range summary
git log <base_tag>..<release_branch> --oneline
```

If the tag or branch isn't found, tell the user and stop.

---

## Step 2 — Auto-detect version and date

Extract version and release date automatically:

```bash
# Try to extract version from release branch name
# e.g. release/1.3.0 → 1.3.0, v1.3.0-rc1 → 1.3.0-rc1
echo "<release_branch>" | sed -nE 's/.*([0-9]+\.[0-9]+\.[0-9]+[a-zA-Z0-9._-]*).*/\1/p'
```

```bash
# Release date = today in "1st January, 2026" format
date +"%d %b, %Y" | sed 's/^0//' | sed 's/ 0/ /'
```

```bash
# Previous version = base tag (strip leading 'v' if present)
echo "<base_tag>" | sed 's/^v//'
```

Use these to populate the document header. If the version can't be parsed from the branch name, ask the user for it.

---

## Step 3 — Gather git data

Run both of these. They are the raw material for AI analysis.

```bash
# 1. Full commit log with metadata
git log <base_tag>..<release_branch> \
  --pretty=format:"COMMIT|%H|%s|%an|%ad" \
  --date=short

# 2. Stat-level diff (files changed, no full content yet)
git diff --stat <base_tag>..<release_branch>

# 3. Full diff (for deep analysis — can be large)
git diff <base_tag>..<release_branch>
```

> ⚠️ If the full diff exceeds ~4000 lines, use file-by-file diffing instead:
>
> ```bash
> # Get list of changed files
> git diff --name-only <base_tag>..<release_branch>
>
> # Then diff each file individually, prioritizing non-test, non-generated files
> git diff <base_tag>..<release_branch> -- <file>
> ```

---

## Step 4 — Analyze and classify changes

With the commit messages and diff in hand, classify every meaningful change into one of:

| Category | What belongs here |
|---|---|
| **✨ New Features** | New capabilities, endpoints, UI, commands, config options |
| **🐛 Bug Fixes** | Defect corrections, crash fixes, incorrect behavior resolved |
| **💥 Breaking Changes** | Removed/renamed APIs, changed signatures, config format changes, dropped support |
| **🔄 Migration Guide** | Step-by-step instructions required due to breaking changes |
| **🔧 Internal / Chores** | Refactors, dependency bumps, CI changes (include briefly or omit) |

**Classification rules:**
- Use the diff to understand *what* changed, use the commit message to understand *why*
- Merge commits and version-bump commits should be skipped
- If a commit is ambiguous, lean on the diff to classify it
- Breaking changes **must** have a corresponding migration guide entry
- Be specific: "Added `--dry-run` flag to `deploy` command" not "Added a flag"

---

## Step 5 — Write the release document

Use this structure. Omit sections that have no content.

```markdown
# Release Notes — v<VERSION>

**Release Date:** <DATE>
**Previous Version:** <BASE_TAG>
**Branch:** <RELEASE_BRANCH>

---

## ✨ New Features

<!-- One bullet per feature. Lead with the user-facing impact, then how. -->
- **<Feature name>**: <What it does and why it matters>

---

## 🐛 Bug Fixes

- **<Component/area>**: <What was broken and what was fixed>

---

## 💥 Breaking Changes

> ⚠️ The following changes require action before upgrading.

- **<Change>**: <What changed and what it affects>

---

## 🔄 Migration Guide

### <Breaking change title>

<Step-by-step instructions to migrate. Include before/after code snippets where relevant.>

**Before:**
```<lang>
<old code>
```

**After:**
```<lang>
<new code>
```

---

## 🔧 Internal Changes

<Optional. Brief summary only — one line per item or a short paragraph.>

---

*Generated from \`git diff <base_tag>..<release_branch>\`*
```

---

## Step 6 — Save the output file

Name the file after the version:

```
release-notes-v<VERSION>-<DATE>.md
```

Example: `release-notes-v1.3.0-2025-09-15.md`

Save it to:
1. The repo root (or a `/docs/releases/` folder if that exists)

If the user provided an existing release doc as a structural reference, mirror its section order and heading style, but do not copy its content.

---

## Quality checklist before presenting

- [ ] Version and date are correct and auto-detected (or confirmed with user)
- [ ] Every breaking change has a migration guide entry
- [ ] No raw commit hashes in the final doc
- [ ] Migration guide has before/after code where applicable
- [ ] File is saved to outputs and presented with `present_files`
- [ ] Final message tells the user the version range covered

---

## Edge cases

| Situation | Handling |
|---|---|
| No commits between tag and branch | Tell the user — don't generate an empty doc |
| Tag doesn't exist | Ask user to check with `git tag -l` |
| Diff is enormous (monorepo, etc.) | Sample top 20 changed files by lines changed; note this in the doc |
| No breaking changes | Omit the Breaking Changes and Migration Guide sections entirely |
| Branch name has no semver | Ask user for the version string |
| Repo has submodules | Diff the parent repo only; note submodule pointer changes separately |

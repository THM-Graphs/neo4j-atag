# 9. Releases are triggered by pushing a version tag

Date: 2026-11-25

## Status

Accepted

## Context

A release was started by hand through a `workflow_dispatch` form: the version was typed in,
the workflow bumped the `pom.xml`, deployed, committed the bump to `main`, and only then
created the tag. This had several weaknesses:

* it could run on any branch and the typed version was not validated,
* the tag was created last, so a failing push left a published package without a tag,
* the version bump was pushed directly to `main`, which conflicts with branch protection,
* the documentation site was published by a separate manual step and could lag behind.

## Decision

* A release is a pushed tag `vX.Y.Z` (`.github/workflows/release.yml`). The tag is the single
  source of truth for the version; nothing is committed back to `main`.
* The workflow first verifies that the tagged commit is reachable from `main` and runs the
  full build (job `verify`, read-only permissions).
* Only then the job `release` (the only one with write permissions) sets the Maven version
  to the tag, deploys to GitHub Packages and creates the GitHub release with the JAR.
* The documentation site is published by the same run, by calling the reusable workflow
  `publish-docs.yml`; it can still be started by hand.
* Releases are serialized (`concurrency: release`, no cancelling).
* The `pom.xml` on `main` keeps its last version number; it is not a release indicator.

Supporting changes in the same step: the build runs on Java 17 and 21 with a timeout,
publishes test reports on failure and the JAR and coverage report as artifacts, and builds
the site on every pull request. Pull requests are checked for a conventional-commit title and
for vulnerable dependencies, CodeQL scans the code, all actions are pinned to commit SHAs
(Dependabot keeps them current) and Maven updates are grouped.

## Consequences

* Releasing is `git tag vX.Y.Z && git push origin vX.Y.Z` (see "Building & Deployment").
* A failed release leaves no published artifact, because the tag is already set and the
  checks run before the deploy. Re-running means deleting the tag and pushing it again.
* Anyone who may push tags can release. Tag protection (a repository ruleset for `v*`)
  should restrict this to maintainers.
* The version in the `pom.xml` of `main` is stale between releases.

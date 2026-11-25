# Building & Deployment

## Building

```bash
mvn clean verify                   # compile, test, package
mvn clean package -DskipTests      # faster, no tests
mvn clean verify -P coverage       # with JaCoCo, report in target/site/jacoco/index.html
```

The plugin JAR is `target/neo4j-atag-<version>.jar`; Jsoup and Saxon-HE are shaded into it.
The site is built with `mvn site site:stage`.

## Continuous integration

| Workflow | Trigger | Purpose |
|---|---|---|
| `build.yml` | push to `main`, pull requests | build and tests on Java 17 and 21, site build, dependency review and conventional-commit title check for pull requests; uploads the JAR and the coverage report, and the test reports if the build fails |
| `codeql.yml` | push to `main`, pull requests, weekly | CodeQL code scanning |
| `release.yml` | push of a tag `vX.Y.Z` | release, see below |
| `publish-docs.yml` | called by `release.yml`, or by hand | publishes this site to `gh-pages` |

All actions are pinned to commit SHAs; Dependabot updates them and the Maven dependencies
(monthly, plugins and test libraries grouped).

## Releasing

A release is started by pushing a tag. Make sure the commit to release is on `main` and green,
then:

```bash
git checkout main && git pull
git tag v0.5.5
git push origin v0.5.5
```

The tag must look like `vX.Y.Z`. The workflow `release.yml` then

1. checks that the tagged commit is reachable from `main` and runs the full build,
2. sets the Maven version to the tag, deploys the package to GitHub Packages and creates
   the GitHub release (generated release notes, plugin JAR attached),
3. publishes the documentation site.

Nothing is committed back to `main`; the version in `pom.xml` is only changed inside the
workflow run, so it can be stale on `main` between releases. Only one release runs at a time.

If a release fails, nothing is published before the checks have passed. Fix the problem,
delete the tag (`git push --delete origin v0.5.5 && git tag -d v0.5.5`) and push it again.

The decision is recorded in `docs/adr/0009-tag-driven-release.md`.

Maintainers should restrict who may create `v*` tags (repository settings, rules, tag ruleset),
because every pushed tag releases.

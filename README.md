![Build status](https://github.com/navikt/isoppfolgingstilfelle/workflows/main/badge.svg?branch=master)

# isoppfolgingstilfelle
Isoppfolgingstilfelle contains pieces of a Oppfolgingstilfelle and calculates relative dates in Sykefraværsoppfølging.

## Technologies used

* Docker
* Gradle
* Kotlin
* Ktor
* Postgres

##### Test Libraries:

* JUnit
* Mockk

#### Requirements

* JDK 17

### Build

Run `./gradlew clean shadowJar`

### Lint (Ktlint)
##### Command line
Run checking: `./gradlew --continue ktlintCheck`

Run formatting: `./gradlew ktlintFormat`
##### Git Hooks
Apply checking: `./gradlew addKtlintCheckGitPreCommitHook`

Apply formatting: `./gradlew addKtlintFormatGitPreCommitHook`

### Pipeline
Pipeline is run with Github Action workflows.
Commits to Master-branch is deployed automatically to dev-gcp and prod-gcp.
Commits to non-master-branch is built without automatic deploy.

This application produces the following topic(s):

* teamsykefravr.isoppfolgingstilfelle-oppfolgingstilfelle-person

This application consumes the following topic(s):

* flex.syketilfellebiter

## Contact

### For NAV employees

We are available at the Slack channel `#isyfo`.

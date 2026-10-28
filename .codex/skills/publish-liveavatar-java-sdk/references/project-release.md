# Project release facts

Read this after the live POMs. If they disagree, the POMs win and the discrepancy must be reported before release.

## Current Maven layout

| Path | Role | Release handling |
|---|---|---|
| `pom.xml` | Aggregator `io.github.newportai-lab:liveavatar-channel-parent:1.0.0-SNAPSHOT` with two modules | Not the Central release entry point |
| `liveavatar-channel-sdk/pom.xml` | Standalone JAR `io.github.newportai-lab:liveavatar-channel-sdk:<version>` | Read the live version; this is the release entry point |
| `liveavatar-channel-server-example/pom.xml` | Spring Boot example inheriting the snapshot parent and depending on a released SDK version | Validate through the reactor, but do not deploy to Central |

The SDK POM does not inherit the root parent. It owns its version, metadata, dependencies, source/javadoc attachments, GPG signing, Central plugin, and `release` profile. Therefore a root reactor deploy can attempt to deploy the snapshot parent and example and is not equivalent to publishing the SDK.

## Current release configuration

- Java source/target/release: 8; project minimum Maven: 3.6+.
- `maven-source-plugin:3.3.0` attaches `-sources.jar`.
- `maven-javadoc-plugin:3.6.3` attaches `-javadoc.jar`.
- `maven-gpg-plugin:3.1.0` signs during `verify`; signing is skipped by default and enabled by profile `release`.
- The GPG plugin reads its passphrase from `${env.GPG_PASSPHRASE}`. This is a fallback for a local, ignored `.env`; the private key remains in the user's GPG keyring.
- `org.sonatype.central:central-publishing-maven-plugin:0.11.0` is a build extension that reads its credentials from the Maven server entry selected by `publishingServerId=central`. Its default `autoPublish=false` preserves the manual Portal publication step.
- There is no `distributionManagement` in the checked-in POMs. Central upload behavior comes from the Central publishing extension plus the user-level Maven server entry.

## Local verification

From the repository root:

```bash
mvn test -Dgpg.skip=true
mvn -f liveavatar-channel-sdk/pom.xml clean verify -Dgpg.skip=true
```

Inspect `liveavatar-channel-sdk/target/` for:

```text
liveavatar-channel-sdk-<version>.jar
liveavatar-channel-sdk-<version>-sources.jar
liveavatar-channel-sdk-<version>-javadoc.jar
```

## Version, changelog, and documentation gate

Use the most recent published release tag or its release commit as the comparison base. If that base cannot be identified unambiguously, stop and ask rather than choosing a convenient commit.

Before deployment, produce a short evidence table covering every item below. Any unexplained mismatch blocks release:

1. Read the SDK version directly from `liveavatar-channel-sdk/pom.xml`. It must be the intended non-`SNAPSHOT` version and must not already exist in Maven Central.
2. Confirm the first release entries in both `CHANGELOG.md` and `CHANGELOG.zh-CN.md` use exactly that version and the intended release date. Compare their listed changes semantically; neither language may omit a user-visible change present in the other.
3. Diff production sources and public signatures against the comparison base. Every added, changed, deprecated, or removed public class, constructor, method, field, enum value, configuration option, callback, and observable behavior must be represented in both changelogs.
4. Inspect Javadoc for every changed public API. New or changed public elements need accurate parameter, return-value, exception, lifecycle, format, unit, and compatibility documentation where applicable. A successfully generated javadoc JAR is necessary but does not prove the content is adequate.
5. Map user-facing changes to `README.md` and `README.zh.md`. Usage, setup, configuration, and examples must remain semantically aligned in both languages. Update them when the public way to use the SDK changes.
6. Map wire-format, event, state-machine, limits, or compatibility changes to `PROTOCOL.md` and `PROTOCOL.zh.md`. Both language versions must describe the same protocol behavior.
7. Search documentation and example POMs/code for stale released-version literals or obsolete API examples. In particular, decide whether `liveavatar-channel-server-example/pom.xml` must reference the release being prepared.

Not every internal implementation change requires README or protocol edits. “No documentation change required” is acceptable only when the evidence table identifies the change as internal and explains why public API, behavior, configuration, examples, and wire protocol are unaffected. Do not silently infer that documentation is current merely because files were modified.

Before requesting deploy confirmation, run `mvn -f liveavatar-channel-sdk/pom.xml clean verify -Prelease` and confirm signatures exist for the POM and all three JARs. This proves signing readiness but does not freeze the bytes Maven will rebuild during deploy.

When the agent cannot unlock the key, a repository-root `.env` may contain only the fallback secret needed here:

```dotenv
GPG_PASSPHRASE=the-local-passphrase
```

Before reading it, verify `git check-ignore .env` succeeds, `git ls-files .env` and `git diff --cached -- .env` show it is neither tracked nor staged, and its permissions are owner-only (normally `0600`). Parse only the single assignment as data without sourcing the file. Pass it only to the Maven process and never print it. The skill must not create or modify `.env` unless the user explicitly asks.

## Deployment and remote checks

The release command is scoped to the SDK POM:

```bash
mvn -f liveavatar-channel-sdk/pom.xml clean deploy -Prelease
```

Do not add `-Dgpg.passphrase=<secret>` in an assistant-run command. Maven deploy performs a new lifecycle build, so the pre-deploy preview confirms source state and intent rather than exact artifact hashes.

After Maven reports a successful Central deployment, immediately create a manifest in a system temporary directory:

```text
<python> <skill>/scripts/release.py record --repo <repository-root> --manifest <temporary-release.json>
<python> <skill>/scripts/release.py check <temporary-release.json>
```

The helper never builds, signs, uploads, publishes, or reads credentials. It requires the actual post-deploy POM, main JAR, sources JAR, javadoc JAR, and all four `.asc` files. Show the deployment identifier, manifest path, file sizes and SHA-256 values, plus the manifest's own SHA-256 before pausing for manual review at <https://central.sonatype.com/publishing>.

After the account holder publishes, check:

- `https://central.sonatype.com/artifact/io.github.newportai-lab/liveavatar-channel-sdk/<version>`
- `https://repo1.maven.org/maven2/io/github/newportai-lab/liveavatar-channel-sdk/<version>/`

After the account holder publishes, run:

```text
<python> <skill>/scripts/release.py verify <temporary-release.json> --expected-manifest-sha256 <approved-sha256>
```

Use exactly the manifest SHA-256 shown before the account holder reviewed the deployment. The helper rejects a changed manifest, reconstructs Maven Central URLs rather than trusting URLs from the file, rechecks local files, and compares all eight remote SHA-256 values. Treat temporary search-index lag as pending verification, not as permission to redeploy. A missing file or hash mismatch blocks a success claim.

## Project-specific questions before a real release

- Is the SDK POM version the intended release, and has the changelog been updated for it?
- Should the example dependency version be updated in the same source change even though the example is not published?
- Is a `central` Maven server token already configured for the account that owns the namespace?
- Which GPG key is authorized for this namespace, and does its agent work non-interactively or require the account holder's terminal?
- Does the current Central plugin configuration leave the deployment pending for manual publication as expected? Confirm from the created deployment rather than assuming Maven success means public availability.

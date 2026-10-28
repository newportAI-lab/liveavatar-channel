---
name: publish-liveavatar-java-sdk
description: Use when preparing, staging, publishing, or verifying a liveavatar-channel-sdk release through Maven, GPG signing, and Sonatype Central Publishing.
---

# Publish LiveAvatar Java SDK

Release only `io.github.newportai-lab:liveavatar-channel-sdk` from this repository. Keep local validation, Central deployment, account-holder publication, and remote verification as distinct states.

Before acting, read the repository `AGENTS.md`, all three POMs, both changelogs, and [references/project-release.md](references/project-release.md). Re-derive facts from live files; the reference records stable project rules, not permission to publish.

## Release contract

1. **Inspect** — Confirm the repository root, intended non-`SNAPSHOT` SDK version, coordinates, Git branch/status/diff, Maven/Java/GPG availability, and the `central` server configuration. List uncommitted files that could affect the artifact. Never assume they should be included.
2. **Validate** — Run the repository test command, enforce the version/changelog/documentation gate, build without uploading, and prove signing works. Fail closed on test, documentation, build, javadoc, signing, or existing-version problems.
3. **Preview** — Report the SDK version, module POM, coordinates, Git HEAD/status/diff, Central target, and exact deploy command. Explain that Maven deploy rebuilds and that approval covers this source state and command, not pre-deploy artifact bytes. Ask for explicit confirmation for this deployment; an earlier request to prepare or inspect is not confirmation.
4. **Deploy and record** — Only after confirmation, run `mvn -f liveavatar-channel-sdk/pom.xml clean deploy -Prelease` using secured Maven and GPG configuration. Capture the Central deployment identifier or URL. Immediately use `scripts/release.py record` to record the actual post-deploy POM, three JARs, four signatures, sizes, and SHA-256 values in a manifest outside the repository. Re-run `check` before relying on it. On timeout or ambiguity, inspect Central before retrying.
5. **Pause for account holder** — Report the deployment identifier and manifest path/hash, state “deployed to Central; not yet published,” and provide <https://central.sonatype.com/publishing>. Stop while the account holder signs in with GitHub, reviews the deployment, completes MFA, and clicks Publish. Never automate or request credentials for this step.
6. **Verify remotely** — Continue only after the user says publication is complete. Run `scripts/release.py verify` with the manifest SHA-256 reported before manual publication. The helper reconstructs every URL from the fixed Maven Central repository, strict coordinates, and exact artifact set, then compares all remote hashes. Allow bounded propagation retries without redeploying, then report “published and remotely verified.”

## Credential boundary

- Never request, echo, log, commit, or place GPG passphrases, Central tokens, GitHub credentials, or MFA codes in command text.
- Keep the private key in the account holder's GPG keyring and the Central token in the user-level Maven `central` server entry. Check availability, never values.
- Obtain `GPG_PASSPHRASE` in this order: the current process; the user's normal environment; a safe repository `.env`; finally hidden input in the account holder's terminal. For `.env`, enforce the checks and parsing rules in the reference. Never store the private key there.
- Pass secrets only to the Maven child process. Never request, echo, log, commit, persist, or place them in assistant-visible command text. While present, do not run effective-POM/debug output, environment dumps, or process inspection.
- If signing still requires `-Dgpg.passphrase=...`, stop and give the command skeleton to the account holder. They must enter and run the real command in their own hidden-input terminal; Codex must not receive or interpolate the passphrase.

## Stop conditions

- Existing remote version: stop; do not overwrite or invent a new version.
- Dirty tree or any POM, changelog, public-API, Javadoc, README, or protocol-documentation mismatch: show the evidence and ask what belongs in this release.
- Failed checks or missing artifacts/signatures: stop; do not skip validation.
- Unclear deploy result: reconcile the Central deployment list before retrying.
- Changed or missing manifest files, or remote hash mismatch: stop; do not publish, redeploy, or substitute artifacts silently.
- Do not commit, push, tag, change versions, close/drop deployments, or perform a real deployment unless the user explicitly requests that specific action.

## Status vocabulary

| Evidence | Report exactly |
|---|---|
| Local checks only | Prepared/validated; nothing uploaded |
| Maven upload accepted | Deployed to Central; awaiting manual publication |
| User clicked Publish | Publication requested; remote verification pending |
| Central files verified | Published and remotely verified |

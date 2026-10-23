# Security policy

PockyAPP is built around privacy and end-to-end encryption, so security reports are taken
seriously.

## Reporting a vulnerability

Please write to **dev@pokecio.com** and do not open a public issue. Include:

- what is affected (Android app, web app, core) and which version,
- steps to reproduce or a proof of concept,
- the impact you expect.

You will get an answer within a few days. Please give us reasonable time to release a fix before
sharing details publicly.

## Scope

- the Rust core (`core/`), including encryption, key backup and verification,
- the Android app (`apps/android/`),
- the update mechanism (manifest, SHA-256 of the download and the check that the APK is signed
  with the same certificate as the installed app; the manifest itself is not signed).

The Matrix homeserver software (Synapse) and matrix-rust-sdk have their own security processes;
issues in them are best reported upstream.

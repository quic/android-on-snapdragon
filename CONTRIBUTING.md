# Contributing to QtiVideoExt

Thank you for your interest in contributing to QtiVideoExt.

## Branching Strategy

Contributors should develop changes on branches based on `master` and submit pull requests against `master`.

## Developer Certificate of Origin

All contributions must include a Developer Certificate of Origin signoff. Commit with `-s` or manually include a `Signed-off-by` line in each commit message.

```bash
git commit -s -m "Describe the change"
```

## Pull Requests

Before submitting a pull request:

- Keep the change focused and easy to review.
- Include a clear description of what changed and why.
- Add or update tests where practical.
- Run the relevant Gradle checks before requesting review.
- Do not include proprietary, confidential, personal, or internal-only information.
- Do not link public GitHub issues or pull requests to internal tracking systems.

## Local Development

Open this project in Android Studio from the repository root, or use a local Android Gradle installation with Android SDK 33 available.

Recommended validation before submitting changes:

```bash
./gradlew check
```

If the Gradle wrapper is not available in your checkout, run the equivalent `gradle check` command with a compatible Gradle version and Android Gradle Plugin 7.4.1.

## Review And Triage Expectations

Maintainers should triage issues and pull requests regularly. The target first-response window is one to two weeks, subject to maintainer availability and project priority.

Pull request reviews should consider correctness, compatibility, tests, documentation, security posture, and whether the change follows the existing style.

## Security Issues

Do not report sensitive vulnerabilities in public issues. Follow `SECURITY.md` for vulnerability reporting.

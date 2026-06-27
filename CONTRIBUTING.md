# Contributing to Pixscape Studio Free

Thank you for helping improve Pixscape Studio Free.

Pixscape Studio Free is the open-source foundation of Pixscape. It is not a
trial version. Pixscape Pro is separate, optional, proprietary, and focused on
advanced production tools. Pro/private features are not part of this public
Free repository.

## Requirements

- JDK 21, matching `javaVersion=21` in `gradle.properties`
- Gradle wrapper from this repository

## Build and Test

From a clean clone:

```powershell
.\gradlew.bat --console=plain compileJava test :html-player:compileJava
```

On Unix-like systems:

```sh
./gradlew --console=plain compileJava test :html-player:compileJava
```

Generated directories such as `.gradle/`, `build/`, `html-player/build/`, and
`html-player/war/` are local outputs and should not be committed.

## HTML Preview Template

The directory `src/main/resources/html-preview-template/` is intentionally
versioned. It contains the prebuilt GWT HTML preview player used by Studio so a
clean clone can run the preview flow without every contributor regenerating the
GWT bundle manually.

Do not remove or replace that template casually. Maintainers may refresh it
when the HTML preview player changes.

HTML player JavaScript must keep the Apache-2.0 SPDX notice and LibreJS
`@license` / `@license-end` markers. Regenerate it with
`buildHtmlPreviewTemplate` so generated GWT JavaScript is wrapped
automatically, and run `checkHtmlPlayerLibreJsLicenses` before release.

## Issues and Pull Requests

GitHub Issues are used for reproducible technical bugs in Pixscape Studio Free.

Before opening a bug report:

- Search existing issues to avoid duplicates.
- Use the bug report form.
- Include clear steps to reproduce the issue.
- Explain the expected behavior and the actual behavior.
- Include your Pixscape Studio Free version, operating system, and relevant logs when possible.

General feedback, product ideas, partnerships, questions, or non-technical requests should be sent by email instead:

```text
info@pixscape.games
```

Security vulnerabilities must not be reported as public issues. Please follow the instructions in SECURITY.md.

Please keep pull requests focused. A good pull request usually describes the problem, explains the intended behavior, and includes a small test or manual validation note when practical.

By intentionally submitting a contribution to this repository, you agree that
your contribution is submitted under the Apache License, Version 2.0.

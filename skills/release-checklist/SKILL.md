---
name: release-checklist
description: Verify a release before tagging: build, tests, docs, and rollback.
---

# Release Checklist

1. Build from a clean checkout with the documented command.
2. Run the full test suite and record the exact command and result.
3. Confirm the version in every manifest matches the tag.
4. Check that user-facing docs describe the new behavior.
5. Write down the rollback step before publishing.
6. Publish, then verify the published artifact, not only the local build.

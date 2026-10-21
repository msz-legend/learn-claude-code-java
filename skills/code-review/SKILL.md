---
name: code-review
description: Review a diff for correctness, security, and missing tests before merging.
---

# Code Review

Use this skill when the user asks for a review or before merging a change.

1. Read the diff and the surrounding code, not only the changed lines.
2. Look for correctness bugs first: wrong state, missing error paths, races,
   off-by-one errors, and behavior that differs from the stated intent.
3. Check security boundaries: untrusted input, path traversal, command
   injection, secrets in logs, and authorization checks.
4. Check tests: does a test fail if the change is reverted? Are edge cases
   covered? Do not accept a test that only asserts the code runs.
5. Report findings in severity order with file and line references. If you
   find nothing, say what you checked and what remains unverified.

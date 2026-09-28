# GitHub Labels Reference

Select **1–2 labels** from the PR-eligible list below. Do NOT use issue-only or manual labels.
Label names must match the repository exactly (verify with `gh label list`).

## PR-Eligible Labels (auto-selectable)

| Label     | When to use                                            |
|-----------|--------------------------------------------------------|
| `신규 기능`  | New feature or improvement to an existing feature       |
| `리팩터링`   | Refactoring / code improvement without behavior change |
| `버그`      | Bug fix                                                 |
| `문서화`     | Docs-only changes (README, CONTRIBUTING, comments)     |
| `삭제`      | Removal of a feature, module, or dead code             |

## Off-limits Labels (do NOT assign)

| Label                    | Reason                                                    |
|--------------------------|-----------------------------------------------------------|
| `무효`                     | Issues only                                               |
| `중복됨`                    | Issues only                                               |
| `중지됨`                    | Applied manually when blocked by another PR/issue         |
| `harness sync:하네스 동기화`  | Automation-managed — never auto-assigned                  |
| `bug:버그`                 | Legacy duplicate of `버그` — use `버그` instead              |

## Quick Decision

```
Bug fix?              → 버그
New feature?          → 신규 기능
Refactoring only?     → 리팩터링
Docs only?            → 문서화
Removal / deletion?   → 삭제
Unsure?               → 신규 기능
```

# GitHub Labels Reference

Select only 1–2 PR labels that exist in the repository. Label names must match the actual names reported by `gh label list`.

## PR Labels

| Label | When to use |
|---|---|
| `신규 기능` | New feature or improvement to an existing feature |
| `리팩터링` | Structural or quality improvement without behavior changes |
| `버그` | Bug fix |
| `문서화` | Documentation-only changes, including README, CONTRIBUTING, and comments |
| `삭제` | Removal of a feature, module, or unused code |

## Do Not Auto-Select

- `위험도: 긴급`, `위험도: 높음`, `위험도: 보통`, `위험도: 낮음`, `위험도: 정리`: Issue-only risk labels. Never add them to PRs automatically.
- `무효`, `중복됨`: Issue-only labels.
- `중지됨`: Apply manually when work is blocked by another Issue or PR.
- `harness sync:하네스 동기화`: Managed by automation; never auto-select it.

## Selection Guide

```
Bug fix?                         -> `버그`
New feature or behavior change?  -> `신규 기능`
Structural improvement only?     -> `리팩터링`
Documentation only?              -> `문서화`
Feature, code, or config removal? -> `삭제`
Unsure?                          -> `신규 기능`
```

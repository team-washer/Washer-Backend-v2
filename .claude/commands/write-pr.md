---
description: Write PR body based on template and commits
---

Write a Pull Request body following these rules:

## Requirements
- Read PR template from `.github/PULL_REQUEST_TEMPLATE.md`
- Analyze current commits to understand changes
- Write objective facts only (WHY, HOW, CURRENT STATE)
- NO promotional language or merge justification
- NO emojis
- Korean language only
- Save to `PR_BODY.md` in project root (overwrite if exists)

## Steps

1. Read PR template structure:
   ```bash
   cat .github/PULL_REQUEST_TEMPLATE.md
   ```

2. Get commit history from base branch (develop or master):
   ```bash
   git log origin/develop..HEAD --oneline --no-merges
   git log origin/develop..HEAD --no-merges
   ```
   If develop doesn't exist, try master:
   ```bash
   git log origin/master..HEAD --oneline --no-merges
   git log origin/master..HEAD --no-merges
   ```

3. Analyze commits to understand:
   - WHY: What problem was solved or what feature was added
   - HOW: What approach was taken
   - CURRENT STATE: What is the result

4. Write PR body following template format:
   - Use template sections (개요, 본문)
   - Keep it objective and factual
   - No unnecessary sections (기대효과, 결과 등)
   - Focus on: why this work was done, how it was implemented, current state

5. Write to `PR_BODY.md` in project root:
   - Use Write tool to create/overwrite the file
   - Follow template structure exactly
   - Korean language only
   - No emojis

## Example Structure

```markdown
## 개요

[1~3 sentences summarizing the work objectively]

## 본문

### 작업 이유
[Why this work was necessary]

### 구현 방식
[How it was implemented]

### 현재 상태
[Current state after the changes]
```

## Important Notes
- DO NOT include promotional language
- DO NOT include merge justifications
- DO NOT use emojis
- Focus on objective facts only
- Always overwrite existing PR_BODY.md

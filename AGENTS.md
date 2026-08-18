## Skill: Commit Message + Changelog

Trigger:

- Any user request asking for a commit message.
- Any user request asking for a changelog.
- Wording does not need to be exact (examples: "the changelog", "write commit msg", "can you prepare release notes").

When this intent is detected, return exactly 1 fenced code block using ```md containing both sections separated by a horizontal rule.

Output wrapper contract (strict):

- Return exactly 1 fenced code block using ```md.
- The block contains the semantic commit message section first, then a separator line (---), then the GitHub changelog section.
- Do not output any plain text before or after the fenced block.

### 1) Semantic commit message section (short resume)

- First line must be semantic: feat:, fix:, refactor:, perf:, test:, docs:, chore:, build:, ci:, or style:
- Subject must be short and specific
- Add bullets with concise technical scope by area (UI, Service, Controller, Repository, Entity, DB, Security, Dependency)
- Plain text only in this section: no backticks, no bold, no italic, no links, no markdown code formatting.
- If a class/file/field/method name is mentioned in section 1, write it as plain text.

### 2) GitHub changelog comment section (bigger changelog)

- Separated from section 1 by a --- line
- Start with `## Changes (<commit_link>)`
- Use markdown code formatting for technical identifiers: `` `fieldName` ``, `` `ClassName` ``, routes, SQL columns, etc.
- Use **bold** for major impact points when useful
- Finish with pending review line with placeholders

Template:

```md
<type>: <short title>

- <Area>: <change>
- <Area>: <change>
- <Area>: <change>

---

## Changes (<commit_link>)

- <Area>: <detailed summary with `identifiers`>.
- <Area>: <detailed summary>.
- <Area>: <migration/dependency/security/testing note>.

## Status: _PENDING Review_ by **[<ReviewerName>](ReviewerProfileLink)**
```

Selection hints:

- feat: new feature/capability
- fix: bug fix
- refactor: internal restructuring without behavior change
- perf: performance improvement
- docs: documentation only

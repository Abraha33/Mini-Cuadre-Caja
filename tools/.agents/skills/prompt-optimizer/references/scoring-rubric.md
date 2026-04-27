## Prompt Optimizer — Scoring Rubric (1–10)

This rubric defines anchor behaviors for the six dimensions used by the Prompt Optimizer skill.
Use these anchors to justify scores with **evidence from the text** (quotes or explicit omissions).

Scoring convention:
- **1–3**: major gaps; output quality is unreliable
- **4–6**: usable but inconsistent; clear improvement opportunities
- **7–8**: strong; minor refinements
- **9–10**: excellent; minimal risk of regression

---

## Prompt Mode rubric

### C — Clarity

- **10**: Terms are unambiguous; task intent is explicit; definitions provided for any domain jargon; no contradictory instructions.
- **8**: Mostly clear; a few terms could be interpreted in more than one way but likely safe.
- **6**: Some ambiguous phrasing; missing definitions; readers must infer meaning.
- **3**: Confusing; conflicting instructions; unclear deliverable.
- **1**: Fundamentally unclear; model cannot reliably infer what is wanted.

Evidence checklist:
- Are key nouns defined? (e.g., “summary”, “report”, “analysis”, “fix”)
- Are constraints stated in measurable terms?
- Are must/should/may used consistently?

### S — Specificity

- **10**: Explicit output format; constraints and acceptance criteria defined; examples included when helpful.
- **8**: Output format mostly clear; a few constraints implicit.
- **6**: Some constraints present; output format incomplete; no acceptance criteria.
- **3**: Mostly high-level; “do X” with little guidance.
- **1**: Purely vague (“help me”, “improve this”) with no constraints.

Evidence checklist:
- Required sections / headings specified?
- Length, tone, audience, and format constraints specified?
- Inputs/outputs and edge cases specified?

### T — Structure

- **10**: Clean hierarchy; grouped requirements; consistent numbering; easy to skim; no duplication.
- **8**: Good structure; minor re-ordering would improve flow.
- **6**: Mixed ordering; some duplication; requirements scattered.
- **3**: Hard to scan; inconsistent structure; important constraints buried.
- **1**: No structure; stream-of-consciousness.

Evidence checklist:
- Are steps in a logical order?
- Are “must” constraints separated from “nice-to-haves”?

### O — Completeness

- **10**: Includes all necessary context; handles edge cases; defines error handling; provides examples for tricky parts.
- **8**: Covers most needs; minor edge cases missing.
- **6**: Covers base case only; misses common edge cases or failure modes.
- **3**: Missing key context; model must guess assumptions.
- **1**: Bare instruction with no context.

Evidence checklist:
- Are failure conditions and what to do on failure defined?
- Are examples present when ambiguity risk is high?

### E — Efficiency

- **10**: Every sentence adds constraints or context; zero fluff; no redundant restatement.
- **8**: Mostly efficient; a few redundant lines.
- **6**: Noticeable repetition; non-essential prose.
- **3**: Verbose; many lines don’t change behavior.
- **1**: Extremely bloated; high token waste.

Evidence checklist:
- Any repeated constraints?
- Any motivational filler that doesn’t change output?

### R — Robustness

- **10**: Strong guardrails; deterministic formatting; “do/don’t” rules; consistent results across runs.
- **8**: Good constraints; occasional variability remains.
- **6**: Some guardrails; likely inconsistent outputs.
- **3**: Many degrees of freedom; high variance.
- **1**: No constraints; unpredictable outputs.

Evidence checklist:
- Are formatting rules strict?
- Are tie-breakers defined when multiple valid outputs exist?

---

## Rules Mode rubric

### C — Clarity

Same anchor logic as Prompt Mode clarity, but applied to **behavior over time**.

### S — Scope Fit

- **10**: Rule covers the intended scope precisely; excludes irrelevant scope; avoids being a “catch-all”.
- **8**: Slightly broad or narrow, but acceptable.
- **6**: Noticeably mis-scoped; will frequently be irrelevant or miss common scenarios.
- **3**: Misleading scope; will cause repeated exceptions.
- **1**: Scope is unclear or harmful.

Evidence checklist:
- Does it say when it applies?
- Does it include explicit non-goals or exclusions?

### T — Structure

- **10**: Highly scannable; clear headings; short bullets; consistent “must/never” formatting.
- **8**: Mostly scannable; minor cleanup needed.
- **6**: Some long paragraphs; important constraints buried.
- **3**: Hard to skim; inconsistent formatting.
- **1**: Unstructured.

### O — Coverage

- **10**: Covers key scenarios without over-specifying; includes exceptions and conflict-resolution guidance.
- **8**: Covers most scenarios; minor gaps.
- **6**: Some gaps or over-specification.
- **3**: Many gaps; will cause repeated confusion.
- **1**: Minimal/no useful coverage.

### E — Efficiency

Same anchor logic as Prompt Mode efficiency, but with higher bar because rules run every conversation.

### R — Composability

- **10**: Avoids conflicts with other rules; explicitly states precedence or compatibility; minimal side effects.
- **8**: Likely compatible; small conflict risks.
- **6**: Some contradictions; could fight other rules.
- **3**: Often conflicts; causes instruction collisions.
- **1**: Fundamentally incompatible or self-contradictory.

Evidence checklist:
- Any absolute directives that could override other system policies?
- Any contradictions with common agent constraints (security, privacy, tool usage)?


---
name: prompt-optimizer
description: >
  Semi-automatic iterative prompt optimization (score → suggest → confirm → revise).
  Supports Prompt Mode (task-specific prompts) and Rules Mode (persistent system-level rules) with auto-detection.
  Trigger: When the user asks to optimize/improve/rewrite a prompt, system prompt, Cursor rule, instructions, or wants an iterative prompt-tuning loop with scoring + version history.
license: Apache-2.0
metadata:
  author: gentleman-programming
  version: "1.0"
---

# Prompt Optimizer

## When to Use

Use this skill when:

- The user provides a prompt and wants it improved without changing intent.
- The user provides a rule/instructions text (e.g. for `.cursor/rules/`) and wants it tightened for consistency and low token cost.
- The user wants a repeatable, versioned, score-driven optimization loop (not a one-shot rewrite).

Do not use this skill when:

- The user wants a brand-new prompt from scratch with no baseline to optimize.
- The user is asking for general advice about prompting (no optimization loop requested).

## Critical Patterns (Non-Negotiable)

### Step 1: Receive Input

Accept either:

- Inline text, OR
- A file path.

If a file path is provided, read the file contents and optimize that text.
If neither text nor file path is provided, ask the user to provide the text to optimize.

### Step 1.5: Auto-Detect Mode

Classify the input as **Prompt** or **Rule** using these signals:

| Signal | Prompt | Rule |
| ------ | ------ | ---- |
| Describes a single task with expected output | Yes | No |
| Uses persistent behavioral language ("always", "never", "when X do Y") | No | Yes |
| Contains role/persona definition for ongoing use | No | Yes |
| Expects a one-time deliverable | Yes | No |
| Located in `.cursor/rules/` or user_rules config | No | Yes |
| References other rules or system-level concerns | No | Yes |

If ambiguous, ask the user to confirm.
Always display the detected mode as the first line of evaluation output:

- `[Mode: Prompt]` or
- `[Mode: Rules]`

### Step 2: Evaluate — 6 Dimensions (1–10)

Use the scoring table matching the detected mode.

#### Prompt Mode dimensions

| Dim | Name | Guiding Question |
| --- | ---- | ---------------- |
| C | Clarity | Would a context-free LLM interpret this unambiguously? |
| S | Specificity | Are constraints, output format, and expected behavior explicit? |
| T | Structure | Is the information logically organized with clear hierarchy? |
| O | Completeness | Does it cover context, examples, edge cases, and error handling? |
| E | Efficiency | Is every sentence carrying necessary information? Zero fluff? |
| R | Robustness | Would 10 runs produce consistent, high-quality outputs? |

#### Rules Mode dimensions

| Dim | Name | Guiding Question |
| --- | ---- | ---------------- |
| C | Clarity | Would any LLM unambiguously understand the behavioral intent? |
| S | Scope Fit | Is the rule's breadth appropriate — not too broad, not too narrow? |
| T | Structure | Is the rule well-organized and easy to scan during every conversation? |
| O | Coverage | Does it handle the relevant scenarios without over-specifying? |
| E | Efficiency | Is the token cost justified given this runs on EVERY conversation? |
| R | Composability | Does this rule coexist peacefully with other rules? No conflicts? |

Composite score:

- Unweighted average of all 6 dimensions (unless the user overrides weights).

For detailed rubrics and anchors, consult:

- `[references/scoring-rubric.md](references/scoring-rubric.md)`

### Step 3: Output the Scorecard (Exact Formats)

**Prompt Mode** scorecard format:

```text
== Prompt Scorecard v{N} ==
Clarity:      {score}/10  {delta}
Specificity:  {score}/10  {delta}
Structure:    {score}/10  {delta}
Completeness: {score}/10  {delta}
Efficiency:   {score}/10  {delta}
Robustness:   {score}/10  {delta}
------------------------------
Composite:    {avg}/10    {delta}

Weakest:  {dimension_name}
Verdict:  {one-line diagnosis}
```

**Rules Mode** scorecard format:

```text
== Rules Scorecard v{N} ==
Clarity:       {score}/10  {delta}
Scope Fit:     {score}/10  {delta}
Structure:     {score}/10  {delta}
Coverage:      {score}/10  {delta}
Efficiency:    {score}/10  {delta}
Composability: {score}/10  {delta}
------------------------------
Composite:     {avg}/10    {delta}

Weakest:  {dimension_name}
Verdict:  {one-line diagnosis}
```

Delta rules:

- For v1, leave `{delta}` blank.
- For v2+, show delta relative to the previous version as `(+1)`, `(-1)`, or `(=)`.

### Step 4: Suggest Improvements (Greedy, Minimal, Targeted)

Focus on the weakest 1–2 dimensions only.

Each suggestion MUST be:

1. **Concrete**: show exact text to add/remove/rewrite (quote the before/after).
2. **Justified**: explicitly name the dimension(s) targeted and why.
3. **Minimal**: smallest change that yields the most score uplift.

### Step 5: User Confirmation (Wait)

Present suggested changes and wait for user response:

- **Confirmed** → apply changes, go to Step 6.
- **Modified** → incorporate user adjustments, then go to Step 6.
- **Rejected** → propose alternative suggestions, return to Step 4.

Do not apply changes without confirmation.

### Step 6: Apply and Re-evaluate

After confirmation:

1. Produce the new version of the prompt/rule (complete text).
2. Re-run the 6-dimension evaluation (Step 2).
3. Output the updated scorecard with deltas.
4. Append to the version history table (Step 7).

### Step 7: Version History (Maintain Throughout Session)

Maintain a running table in the conversation that grows each iteration.
Headers must match the detected mode:

**Prompt Mode**:
`| Version | C | S | T | O | E | R | Composite | Change Summary |`

**Rules Mode**:
`| Version | C | SF | T | Cov | E | Comp | Composite | Change Summary |`

Example:

```text
| Version | C | S | T | O | E | R | Composite | Change Summary |
|---------|---|---|---|---|---|---|-----------|----------------|
| v1      | 5 | 4 | 6 | 3 | 7 | 4 | 4.8       | baseline       |
| v2      | 7 | 4 | 6 | 5 | 7 | 5 | 5.7       | added examples |
```

### Step 8: Termination

Stop iterating when:

- Composite score \( \ge 8.5 \), OR
- The user explicitly says they are satisfied.

On termination, output:

1. The final optimized text (complete).
2. The full version history table.
3. A concise summary of key improvements made.

## Optimization Principles

1. **One thing at a time**: never rewrite the entire text in one iteration; target weakest dimension(s) surgically.
2. **Never break what works**: if a dimension scored 8+, do not touch the text responsible for that score unless absolutely necessary.
3. **Simplicity over cleverness**: if two rewrites achieve the same gain, pick the shorter one.
4. **Evidence over intuition**: justify each score using specific quotes (or explicit absences) from the text.
5. **Respect user intent**: preserve the original purpose; if intent is unclear, ask before changing.

## Resources

- **Scoring rubric**: See [references/scoring-rubric.md](references/scoring-rubric.md)
- **Walkthrough examples**: See [references/examples.md](references/examples.md)

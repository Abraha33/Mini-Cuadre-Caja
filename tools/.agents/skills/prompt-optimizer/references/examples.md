## Prompt Optimizer — Quick Examples

These examples show what a **single iteration** looks like. They are intentionally short.

---

## Example A — Prompt Mode (one iteration)

### Input prompt

“Write me a summary of this.”

### Detected mode

[Mode: Prompt]

### v1 Scorecard (example)

```
== Prompt Scorecard v1 ==
Clarity:      3/10  
Specificity:  1/10  
Structure:    2/10  
Completeness: 1/10  
Efficiency:   9/10  
Robustness:   2/10  
------------------------------
Composite:    3.0/10  

Weakest:  Specificity
Verdict:  Output requirements and context are missing.
```

### Suggestions (target weakest: Specificity, then Completeness)

1) **Rewrite (Specificity)** — replace the entire prompt with:

“Summarize the provided text for a non-technical audience in 5 bullet points. Include 1 bullet for key numbers/dates, and end with a 1-sentence takeaway.”

Justification: Adds explicit audience, format, length, and required elements → higher Specificity and Robustness.

2) **Add (Completeness)** — append:

“If the text is missing, ask me to paste it.”

Justification: Defines an error path instead of guessing → higher Completeness.

### Version history (after applying changes and re-evaluating)

```
| Version | C | S | T | O | E | R | Composite | Change Summary |
|---------|---|---|---|---|---|---|-----------|----------------|
| v1      | 3 | 1 | 2 | 1 | 9 | 2 | 3.0       | baseline       |
| v2      | 7 | 8 | 6 | 5 | 8 | 7 | 6.8       | added format, audience, failure case |
```

---

## Example B — Rules Mode (one iteration)

### Input rule

“Always be helpful and answer quickly.”

### Detected mode

[Mode: Rules]

### v1 Scorecard (example)

```
== Rules Scorecard v1 ==
Clarity:       5/10  
Scope Fit:     3/10  
Structure:     2/10  
Coverage:      2/10  
Efficiency:    8/10  
Composability: 3/10  
------------------------------
Composite:     3.8/10  

Weakest:  Coverage
Verdict:  The rule lacks actionable behaviors and conflict handling.
```

### Suggestions (target weakest: Coverage, then Composability)

1) **Rewrite (Coverage)** — replace with:

“When responding, provide a direct answer first, then (only if needed) add brief context. If the user’s request is ambiguous, ask 1–2 clarifying questions before proposing solutions.”

Justification: Converts vague intent into actionable behaviors and defines an ambiguity branch.

2) **Add (Composability)** — append:

“Follow higher-priority system and safety constraints even if that reduces speed.”

Justification: Reduces conflict with other rules by explicitly acknowledging precedence.

### Version history (after applying changes and re-evaluating)

```
| Version | C | SF | T | Cov | E | Comp | Composite | Change Summary |
|---------|---|----|---|-----|---|------|-----------|----------------|
| v1      | 5 | 3  | 2 | 2   | 8 | 3    | 3.8       | baseline       |
| v2      | 7 | 7  | 6 | 7   | 7 | 7    | 6.8       | made behaviors explicit; added precedence |
```


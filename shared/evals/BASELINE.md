# Triage eval baseline

**Status:** post-A. `TriageEvalRunnerTest` now scores `DeterministicTriageTarget`.
Numbers match the pre-A stub on this case set, which is expected: Stream A is
an exact port of `priority.ts`.

| Metric | Stub (pre-A) | DeterministicTriage (post-A) |
|--------|-------------:|-----------------------------:|
| Cases | 120 | 120 |
| Level accuracy | 0.775 (93/120) | 0.775 (93/120) |
| URGENT precision | 1.000 (16/16) | 1.000 (16/16) |
| URGENT recall | 0.727 (16/22) | 0.727 (16/22) |
| Injection pass rate | 1.000 (16/16, hard gate) | 1.000 (16/16) |
| Promo suppression (promo → LOW) | 0.952 (20/21) | 0.952 (20/21) |
| requiresResponse accuracy | 0.867 (104/120) | 0.867 (104/120) |

Injection pass rate is the only Wave 1 hard fail. Other columns stay
report-only until this baseline is agreed.

The stub never raised a non-urgent case to URGENT (precision 1.0). Six
human-labeled URGENT cases scored IMPORTANT — usually weaker wording
(`ASAP`, `due today`, `help me` without `locked`/`hospital` plus a
high-importance contact). Promo suppression missed `promo-020` because
`coupon` sat next to a question mark. Several injection bodies scored
NORMAL rather than LOW; `isInjection` still caught all 16.

Tag counts in the current v2 set (a case may have more than one tag):

| Tag | Count |
|-----|------:|
| (untagged ordinary chat) | 34 |
| urgent | 22 |
| promo | 21 |
| injection | 16 |
| ambiguous | 16 |
| long | 10 |
| emoji | 5 |
| multilingual | 4 |

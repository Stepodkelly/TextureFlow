# Triage eval baseline

**Status:** stub / TypeScript-regex placeholder. The true deterministic
baseline is recorded after Stream A merges `DeterministicTriage` and the
coordinator swaps `DeterministicStubTarget` for that implementation.

Numbers below come from `DeterministicStubTarget` (Java port of the
`priority.ts` regexes and weights) over `triage-cases-v2.json`. Re-run
`TriageEvalRunnerTest` and replace the post-A column after A lands.

| Metric | Stub (pre-A) | DeterministicTriage (post-A) |
|--------|-------------:|-----------------------------:|
| Cases | 120 | — |
| Level accuracy | 0.775 (93/120) | — |
| URGENT precision | 1.000 (16/16) | — |
| URGENT recall | 0.727 (16/22) | — |
| Injection pass rate | 1.000 (16/16, hard gate) | must stay 1.000 |
| Promo suppression (promo → LOW) | 0.952 (20/21) | — |
| requiresResponse accuracy | 0.867 (104/120) | — |

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

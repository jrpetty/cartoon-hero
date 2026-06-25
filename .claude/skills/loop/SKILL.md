---
name: loop
description: >-
  Take a task and iterate on it autonomously, in build → test → verify →
  critique → improve cycles, until it is genuinely complete, proven to work,
  and as good as it can reasonably be — then report honestly. Use when the user
  signals "keep going until it's right" rather than "do one pass": e.g. "loop
  this", "keep looping until it's perfect", "make it the best it can be", "build
  this and don't come back until it works/is tested", "don't involve me until
  it's done", or any open-ended "keep improving X". Not for one-off edits or
  quick questions, and not for fixed-interval scheduling (that's a timer, not
  this).
---

# loop — iterate autonomously until it's actually done

The job is not to *attempt* the task once. It is to **drive the task to a
finished, verified, high-quality state**, looping as many times as that takes,
and only stopping when further work would add no real value or you hit
something only the user can decide.

## 1. First, pin down "done"

Before looping, write down — for yourself — what *finished and perfect* means
for THIS task. Be concrete and testable. Examples:
- "The app builds, every feature works in a real browser with zero console
  errors, and the exported file opens standalone."
- "All tests pass, the new endpoint returns correct data, and edge cases are
  handled."

Calibrate the bar to the request. "Quick fix" → smaller bar, fewer cycles.
"Make it the best it can be / be comprehensive / don't come back until perfect"
→ high bar: broad coverage, adversarial checks, polish passes. When the user's
words imply thoroughness, lean toward more loops, not fewer.

If "done" is genuinely ambiguous in a way that changes what you build, pick the
most reasonable interpretation, **state the assumption, and proceed** — do not
stall asking permission. Only stop for the user on irreversible/destructive
actions or true scope forks.

## 2. Work autonomously

The user has stepped back. Asking "shall I…?" / "want me to…?" blocks the
work and defeats the point. For anything reversible that follows from the
request, **just do it**. Make the best decision you can with the information
available, note it, and keep moving. Never end a turn with a plan or a promise
("I'll next…") — do that work now.

## 3. The loop (one wave)

Repeat this cycle. Keep each wave a coherent, shippable increment.

1. **Plan the next increment.** Smallest change that makes real progress toward
   "done". For big tasks, organize into named waves and track them.
2. **Implement it.** Match the surrounding code/style. Keep it clean.
3. **Test it.** Add/extend automated tests for what you just built.
4. **Verify it for real** (this is where most "done" claims are false — see §4).
5. **Self-critique.** Ask: what's missing, wrong, ugly, fragile, or unverified?
   What would a skeptical reviewer or a real user catch? That list is the next
   wave's input. Spawning a subagent as an adversarial critic/reviewer is a
   good way to find what you're blind to.
6. **Decide:** continue (more value to add or defects found) or stop (§5).

Commit between waves with a clear message describing what changed and why, so
progress is durable and reviewable. Keep the test suite green at every commit.
If the user's workflow includes a branch/PR, push as you go.

## 4. Verification is the whole game

"It should work" / "the code looks right" is **not** verification. Looping
without real verification just multiplies unproven claims. Standards:

- **Actually run it.** Execute the code, start the app, hit the endpoint, open
  the page. Watch for errors; treat any error or warning as a finding.
- **Test the real deliverable, not just units.** Test the artifact the user
  actually receives end-to-end. (In this repo's history: the win was opening
  the *exported standalone HTML file with no server* and driving its buttons —
  not just asserting the renderer returned a string.) Ask "what does the user
  hold at the end?" and test *that*.
- **Exercise interactions and edge cases,** not just the happy path: empty
  input, malicious input (escaping/XSS), large input, reordering, undo, the
  offline/fallback path.
- **Prove it, don't assert it.** Decode the QR, parse the JSON, diff the
  roundtrip, screenshot the UI and actually look at it. Independent checks beat
  self-confirmation.
- **Be ruthlessly honest about what you could NOT verify.** If something needs
  a key, a credential, a device, or network you don't have, say so plainly and
  verify everything up to that boundary (e.g. stub the network, exercise the
  real parsing). Never imply something is proven when it isn't. A confident
  false "it works" is the worst possible outcome of a loop.

If verification fails, that's not the end — it's the next iteration. Diagnose,
fix, re-verify. Re-run the full suite after fixes to catch regressions.

## 5. When to stop

Stop and report when ANY of these is true:
- **Done:** the "done" definition is met and a fresh critique pass surfaces no
  meaningful improvement (visual, technical, correctness, or UX).
- **Diminishing returns:** remaining ideas are genuinely optional polish; note
  them as follow-ups rather than gold-plating forever.
- **Blocked:** you need input only the user can give (a credential, a product
  decision, an irreversible action). Report where you're stuck and why.

"Perfect" is asymptotic — don't loop forever chasing it. Reaching a genuinely
complete, verified, polished state and saying so *is* success.

## 6. Final report

Lead with the outcome. Then: what was built/changed, **what you verified and
how** (with the honest list of anything you couldn't verify and why), how to
run it, and any optional follow-ups. The user should be able to trust the
report without re-checking your work — which is only possible if §4 was real.

## Anti-patterns (don't)
- Ending a wave by claiming success without running anything.
- Stopping after one pass on a task that asked you to "keep going".
- Asking permission for reversible steps the request already implied.
- Hiding or glossing over an unverified/gated part.
- Polishing endlessly past the point of real value instead of reporting.

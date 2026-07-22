---
name: decispher-memory
description: >-
  Save, recall, correct, or forget durable personal and team memory with Decispher.
  Use whenever the user says "remember this", "note that I prefer...", "forget that",
  "what do you remember about me", or states a lasting preference, environment fact,
  or who-owns-what that should carry across sessions and agents.
---

# Decispher memory

Decispher Memory is durable, personally-owned context that Decispher injects into
the right session automatically, with receipts. It is separate from branch-story
capture: decisions, constraints, and abandoned approaches go through
`session_record`, not here.

## When to act

Trigger on durable, first-person or team facts, not on transient task state.

- Preferences and working style: "I prefer pnpm", "keep PRs small".
- Environment facts: "staging runs on port 4000", "the DB is Postgres 16".
- Person and team facts: "Ali owns billing", "we deploy on Fridays".
- Corrections: "actually, use tabs now" replaces the old memory.

Do not save routine progress, one-off task details, or secrets.

## How to act

Use the Decispher MCP tools. Saving a memory costs a small credit (it embeds and
runs a fusion check); recall and dereference are free.

- **Save** with `remember`, one durable statement per call.
  - Set `userRequested: true` ONLY when the human explicitly asked you to
    remember it. Those save active immediately. Anything you volunteer on your
    own is stored as a proposal the owner confirms in their dashboard inbox, so
    do not report it as active.
  - Default scope is the user's private memory. `scope: "project"` or
    `"company"` files an approval request and never shares instantly.
  - The write embeds and fuses before returning, so it can take a few seconds.
    Wait for the reply rather than retrying; it is idempotent, and an equivalent
    memory folds into a confirm.
- **Correct or forget**: there is no agent delete tool, by design. To change a
  fact, `remember` the new truth and it supersedes the old one. To remove a
  memory outright, ask the user to delete it in the dashboard under Memory, My
  Memory. Never claim you deleted something you cannot.
- **Recall**: you do not search memory. Relevant memories arrive in your session
  automatically as a `[decispher memory]` block. When that block is a manifest,
  `get_memory(memoryId)` dereferences a single entry.
- **Attach a set**: a named group of memories rides the session when the task
  text contains `@memory:<slug>`, or when the user runs `/decispher:<slug>`.

## Slash commands (any MCP-capable agent)

- `/decispher:remember <text>` saves a memory as the human's explicit request.
- `/decispher:memory-status` shows who this session attributes to, whether memory
  is on, the sets available, and the most recent injection receipt.

## Attribution

Memory is per person. On a shared or CI key with no identity, personal memory is
off: the session still reads company and shared memory but cannot write private
memory. If the user expects their memory and it is missing, have them run
`decispher setup-memory`, or `decispher whoami` to check which identity resolved.

#!/usr/bin/env node
/**
 * Decispher Session Recorder — hook handler (installed by `npx decispher init`).
 *
 * One self-contained script, zero dependencies, Node >= 18. Subcommands map to
 * Claude Code hooks + the git post-checkout hook + the statusline:
 *
 *   session-start | user-prompt | post-tool | stop | session-end
 *   cursor-after-edit | cursor-before-shell | cursor-stop   (Cursor hooks.json)
 *   codex-session-start | codex-user-prompt | codex-post-tool | codex-stop   (.codex/hooks.json)
 *   grok-session-start | grok-user-prompt | grok-post-tool | grok-stop | grok-session-end   (.grok/hooks/decispher.json)
 *   post-checkout <prev> <next> <flag>
 *   statusline
 *
 * Invariants (ADR-052/053):
 *  - Capture NEVER blocks the agent: every network call has a hard timeout,
 *    every failure buffers locally (.decispher/.session/) and exits 0.
 *  - Redaction happens at source before anything leaves the machine; content
 *    from never-capture paths (.env, keys, credentials) is never read at all.
 *  - Branch is resolved PER EVENT from the event's cwd — mid-session branch
 *    switches land in the right store.
 */

import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const POST_TIMEOUT_MS = 3000;
const BRIEFING_TIMEOUT_MS = 2500;
const EDIT_CHECK_TIMEOUT_MS = 2000;
const STATUS_TIMEOUT_MS = 1500;
const GIT_TIMEOUT_MS = 1500;
const STATUS_CACHE_TTL_MS = 60_000;
const MAX_BUFFERED_EVENTS = 500;
const MAX_TERMINAL_OUTPUT_CHARS = 16_000;
const MAX_DIFF_CHARS = 60_000;
const MAX_PROMPT_CHARS = 16_000;
const MAX_EDIT_HISTORY_PER_FILE = 50;
// RD (ADR-078, D7) — cap for the full-response reasoning provenance, applied
// strictly AFTER redaction (slicing first can cut a secret mid-pattern and
// leak the fragment). Mirrors MAX_REASONING_PROVENANCE_CHARS in
// @decispher/common; keep the two in sync.
const MAX_REASONING_PROVENANCE_CHARS = 64_000;

// ── Redaction at source (mirror of the server-side SessionRedactor) ─────────

const MASK = '[REDACTED]';

const SECRET_PATTERNS = [
    { name: 'pem_block', regex: /-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----/g },
    { name: 'aws_access_key', regex: /\b(?:AKIA|ASIA|ABIA|ACCA)[0-9A-Z]{16}\b/g },
    { name: 'github_token', regex: /\b(?:ghp|gho|ghu|ghs|ghr|github_pat)_[A-Za-z0-9_]{20,255}\b/g },
    { name: 'gitlab_token', regex: /\bglpat-[A-Za-z0-9_-]{20,}\b/g },
    { name: 'slack_token', regex: /\bxox[baprs]-[A-Za-z0-9-]{10,}\b/g },
    { name: 'stripe_key', regex: /\b[rs]k_(?:live|test)_[A-Za-z0-9]{20,}\b/g },
    { name: 'openai_key', regex: /\bsk-[A-Za-z0-9_-]{20,}\b/g },
    { name: 'jwt', regex: /\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b/g },
    { name: 'bearer_header', regex: /\b(Bearer|Basic)\s+[A-Za-z0-9+/_=.-]{16,}/g },
    { name: 'connection_string', regex: /\b(postgres(?:ql)?|mysql|mongodb(?:\+srv)?|redis|amqp):\/\/[^\s:@]+:[^\s@]+@/gi },
    { name: 'assignment', regex: /\b([A-Z0-9_]*(?:SECRET|TOKEN|PASSWORD|PASSWD|API_?KEY|PRIVATE_?KEY|CREDENTIALS?)[A-Z0-9_]*)\s*([:=]\s*|\s+)["']?[^\s"']{8,}["']?/gi },
];

const NEVER_CAPTURE_GLOBS = [
    /(^|[\\/])\.env(\.[^\\/]*)?$/i,
    /\.(pem|key|p12|pfx|jks|keystore|crt|cer|der)$/i,
    /(^|[\\/])id_(rsa|ed25519|ecdsa|dsa)(\.pub)?$/i,
    /(^|[\\/])\.?(npmrc|netrc|pgpass|git-credentials)$/i,
    /(^|[\\/])(credentials|secrets?)\.(json|ya?ml|toml)$/i,
    /(^|[\\/])\.aws[\\/]credentials$/i,
    /(^|[\\/])\.kube[\\/]config$/i,
];

export function redactText(text) {
    let out = text;
    for (const pattern of SECRET_PATTERNS) {
        out = out.replace(pattern.regex, (match, ...groups) => {
            if (pattern.name === 'assignment' && typeof groups[0] === 'string' && typeof groups[1] === 'string') {
                return `${groups[0]}${groups[1]}${MASK}`;
            }
            if (pattern.name === 'bearer_header' && typeof groups[0] === 'string') {
                return `${groups[0]} ${MASK}`;
            }
            if (pattern.name === 'connection_string') {
                return match.slice(0, match.indexOf('://') + 3) + MASK + '@';
            }
            return MASK;
        });
    }
    return out;
}

export function isNeverCapturePath(filePath) {
    return NEVER_CAPTURE_GLOBS.some((glob) => glob.test(filePath));
}

// ── Context ──────────────────────────────────────────────────────────────────

function git(cwd, args) {
    const res = spawnSync('git', args, { cwd, encoding: 'utf8', timeout: GIT_TIMEOUT_MS });
    if (res.status !== 0 || typeof res.stdout !== 'string') return null;
    return res.stdout.trim() || null;
}

export function defaultBranchOf(cwd) {
    return git(cwd, ['rev-parse', '--abbrev-ref', 'HEAD']);
}

function findRepoRoot(startDir) {
    let dir = path.resolve(startDir);
    for (;;) {
        if (fs.existsSync(path.join(dir, '.decispher', 'recorder.json'))) return dir;
        const parent = path.dirname(dir);
        if (parent === dir) return null;
        dir = parent;
    }
}

/**
 * Slot selection over one API URL's credentials — mirrors the CLI's
 * config.ts contract: projects[projectId] → repos[repo] → default apiKey
 * (the default slot doubles as the legacy single-key shape).
 */
export function selectApiKey(urlCreds, { projectId = null, repo = null } = {}) {
    if (!urlCreds) return null;
    if (projectId && urlCreds.projects?.[projectId]?.apiKey) return urlCreds.projects[projectId].apiKey;
    if (repo && urlCreds.repos?.[repo]?.apiKey) return urlCreds.repos[repo].apiKey;
    return urlCreds.apiKey ?? null;
}

function loadApiKey(apiUrl, scope = {}) {
    if (process.env.DECISPHER_API_KEY) return process.env.DECISPHER_API_KEY;
    try {
        const credsPath = path.join(os.homedir(), '.decispher', 'credentials.json');
        const creds = JSON.parse(fs.readFileSync(credsPath, 'utf8'));
        return selectApiKey(creds[apiUrl], scope);
    } catch {
        return null;
    }
}

/**
 * ADR-077 — the identity key (WHO) travels beside the api key (WHAT). Without
 * it a shared team key resolves no principal and personal memory silently goes
 * dark, so the hook must forward it exactly as the MCP client does. Mirrors
 * `loadIdentityKey` in config.ts: env first, then the home cred store, keyed by
 * apiUrl. Never read from a repo file — the CLI refuses to write one.
 */
function loadIdentityKey(apiUrl) {
    if (process.env.DECISPHER_IDENTITY_KEY) return process.env.DECISPHER_IDENTITY_KEY;
    try {
        const credsPath = path.join(os.homedir(), '.decispher', 'credentials.json');
        const creds = JSON.parse(fs.readFileSync(credsPath, 'utf8'));
        return creds[apiUrl]?.identityKey ?? null;
    } catch {
        return null;
    }
}

// ── Resolved-project cache (.decispher/.session/project.json) ────────────────
// Written by init and refreshed from every session-start briefing, so a
// dashboard remap switches this repo to the right project key next session.

function projectCachePath(sessionDir) {
    return path.join(sessionDir, 'project.json');
}

export function readCachedProjectId(sessionDir) {
    try {
        const parsed = JSON.parse(fs.readFileSync(projectCachePath(sessionDir), 'utf8'));
        return typeof parsed.projectId === 'string' ? parsed.projectId : null;
    } catch {
        return null;
    }
}

export function writeCachedProjectId(sessionDir, projectId) {
    fs.mkdirSync(sessionDir, { recursive: true });
    fs.writeFileSync(
        projectCachePath(sessionDir),
        `${JSON.stringify({ projectId, resolvedAt: new Date().toISOString() })}\n`,
    );
}

// ── Per-developer capture pause (~/.decispher/state.json) ────────────────────
// A machine-wide, time-boxed switch shared with the CLI and the VS Code
// extension. Paused = capture NEW events stops; serving (briefings) keeps
// working. A lapsed timestamp auto-resumes, so recording is never silently off.

const PAUSE_INDEFINITE = 'indefinite';

export function readPauseView(now = Date.now()) {
    try {
        const statePath = path.join(os.homedir(), '.decispher', 'state.json');
        const value = JSON.parse(fs.readFileSync(statePath, 'utf8'))?.pausedUntil;
        if (value === PAUSE_INDEFINITE) return { paused: true, indefinite: true, remainingMs: null };
        if (typeof value === 'string') {
            const ts = Date.parse(value);
            if (Number.isFinite(ts) && ts > now) return { paused: true, indefinite: false, remainingMs: ts - now };
        }
    } catch { /* no state file — capture is live */ }
    return { paused: false, indefinite: false, remainingMs: null };
}

export function isCapturePaused(now = Date.now()) {
    return readPauseView(now).paused;
}

/**
 * Builds the runtime context, or null when the repo isn't initialized.
 * `overrides` exists for tests (fetchImpl, branchOf, root).
 */
export function createContext(startDir, overrides = {}) {
    const root = overrides.root ?? findRepoRoot(startDir);
    if (!root) return null;
    let config;
    try {
        config = JSON.parse(fs.readFileSync(path.join(root, '.decispher', 'recorder.json'), 'utf8'));
    } catch {
        return null;
    }
    const apiUrl = (process.env.DECISPHER_API_URL ?? config.apiUrl ?? '').replace(/\/+$/, '');
    if (!apiUrl || !config.repo) return null;
    const sessionDir = path.join(root, '.decispher', '.session');
    const projectId = overrides.projectId ?? readCachedProjectId(sessionDir);
    const apiKey = overrides.apiKey ?? loadApiKey(apiUrl, { projectId, repo: config.repo });
    const identityKey = overrides.identityKey ?? loadIdentityKey(apiUrl);
    return {
        root,
        repo: config.repo,
        apiUrl,
        apiKey,
        identityKey,
        projectId,
        agent: overrides.agent ?? 'claude-code',
        sessionDir,
        fetchImpl: overrides.fetchImpl ?? globalThis.fetch,
        branchOf: overrides.branchOf ?? defaultBranchOf,
    };
}

export function hashId(...parts) {
    return createHash('sha1').update(parts.join(' ')).digest('hex').slice(0, 20);
}

export function buildEvent(ctx, kind, cwd, fields = {}) {
    const branch = ctx.branchOf(cwd || ctx.root) ?? 'HEAD';
    return {
        kind,
        agent: ctx.agent,
        branch,
        repo: { fullName: ctx.repo },
        occurredAt: new Date().toISOString(),
        ...fields,
    };
}

// ── Local buffer + flush (offline-first) ─────────────────────────────────────

function bufferPath(ctx) {
    return path.join(ctx.sessionDir, 'buffer.jsonl');
}

function readBuffer(ctx) {
    try {
        const raw = fs.readFileSync(bufferPath(ctx), 'utf8');
        const events = raw.split('\n').filter(Boolean).map((line) => {
            try { return JSON.parse(line); } catch { return null; }
        }).filter(Boolean);
        // Oldest events drop first when the buffer overflows offline.
        return events.slice(-MAX_BUFFERED_EVENTS);
    } catch {
        return [];
    }
}

function writeBuffer(ctx, events) {
    fs.mkdirSync(ctx.sessionDir, { recursive: true });
    const tmp = `${bufferPath(ctx)}.${process.pid}.tmp`;
    fs.writeFileSync(tmp, events.map((e) => JSON.stringify(e)).join('\n') + (events.length ? '\n' : ''));
    fs.renameSync(tmp, bufferPath(ctx));
}

export function appendToBuffer(ctx, events) {
    fs.mkdirSync(ctx.sessionDir, { recursive: true });
    fs.appendFileSync(bufferPath(ctx), events.map((e) => JSON.stringify(e)).join('\n') + '\n');
}

async function postEvents(ctx, events, timeoutMs) {
    const res = await ctx.fetchImpl(`${ctx.apiUrl}/api/sessions/events`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'x-api-key': ctx.apiKey },
        body: JSON.stringify({ events }),
        signal: AbortSignal.timeout(timeoutMs),
    });
    if (!res.ok) throw new Error(`ingest responded ${res.status}`);
}

/** Drains the buffer in batches of 100; stops at the first failure (retry next event). */
export async function flushBuffer(ctx, timeoutMs = POST_TIMEOUT_MS) {
    if (!ctx.apiKey) return { sent: 0, remaining: readBuffer(ctx).length };
    let pending = readBuffer(ctx);
    let sent = 0;
    while (pending.length > 0) {
        const batch = pending.slice(0, 100);
        try {
            await postEvents(ctx, batch, timeoutMs);
        } catch {
            writeBuffer(ctx, pending);
            return { sent, remaining: pending.length };
        }
        sent += batch.length;
        pending = pending.slice(100);
        writeBuffer(ctx, pending);
    }
    return { sent, remaining: 0 };
}

export async function enqueueEvents(ctx, events) {
    if (events.length === 0) return flushBuffer(ctx);
    appendToBuffer(ctx, events);
    // `remaining > 0` doubles as the offline signal — callers skip optional
    // network round-trips (edit-check) instead of stacking timeouts.
    return flushBuffer(ctx);
}

// ── Edit tracking + rejected-attempt detection ───────────────────────────────

function editStatePath(ctx, sessionId) {
    return path.join(ctx.sessionDir, `edits-${hashId(sessionId)}.json`);
}

export function loadEditState(ctx, sessionId) {
    try {
        return JSON.parse(fs.readFileSync(editStatePath(ctx, sessionId), 'utf8'));
    } catch {
        return {};
    }
}

export function saveEditState(ctx, sessionId, state) {
    fs.mkdirSync(ctx.sessionDir, { recursive: true });
    fs.writeFileSync(editStatePath(ctx, sessionId), JSON.stringify(state));
}

const KEEP_CHARS = 20_000;

/**
 * A rejected attempt is an edit pair (old→new … new→old) on the same file
 * before commit: the original change was introduced then reverted. Returns the
 * earlier (reverted) edit when the incoming edit undoes it, else null.
 */
export function detectRejectedAttempt(fileHistory, oldStr, newStr) {
    const oldHash = hashId(oldStr);
    const newHash = hashId(newStr);
    for (let i = fileHistory.length - 1; i >= 0; i -= 1) {
        const prior = fileHistory[i];
        if (prior.oldHash === newHash && prior.newHash === oldHash) return prior;
    }
    return null;
}

export function recordEdit(state, filePath, oldStr, newStr) {
    const history = state[filePath] ?? [];
    history.push({
        oldHash: hashId(oldStr),
        newHash: hashId(newStr),
        old: oldStr.slice(0, KEEP_CHARS),
        new: newStr.slice(0, KEEP_CHARS),
        at: new Date().toISOString(),
    });
    state[filePath] = history.slice(-MAX_EDIT_HISTORY_PER_FILE);
    return state;
}

export function unifiedDiff(filePath, oldStr, newStr) {
    const removed = oldStr ? oldStr.split('\n').map((l) => `-${l}`).join('\n') : '';
    const added = newStr ? newStr.split('\n').map((l) => `+${l}`).join('\n') : '';
    const body = [removed, added].filter(Boolean).join('\n');
    const diff = `--- a/${filePath}\n+++ b/${filePath}\n${body}`;
    return diff.length > MAX_DIFF_CHARS ? `${diff.slice(0, MAX_DIFF_CHARS)}\n…[truncated]` : diff;
}

// ── Déjà vu / drift edit check (SR-S8) ───────────────────────────────────────
// One bounded POST after an edit stages. Free server-side (embedding-only,
// no LLM, no credits). Fail-open everywhere: offline, timeout, non-200 — the
// edit always proceeds; the warning is a bonus, never a gate.

function warnedCachePath(ctx, sessionId) {
    return path.join(ctx.sessionDir, `warned-${hashId(sessionId)}.json`);
}

export function loadWarnedKeys(ctx, sessionId) {
    try {
        const keys = JSON.parse(fs.readFileSync(warnedCachePath(ctx, sessionId), 'utf8'));
        return Array.isArray(keys) ? keys : [];
    } catch {
        return [];
    }
}

export function saveWarnedKeys(ctx, sessionId, keys) {
    fs.mkdirSync(ctx.sessionDir, { recursive: true });
    fs.writeFileSync(warnedCachePath(ctx, sessionId), JSON.stringify(keys.slice(-200)));
}

function warningKey(warning) {
    return `${warning.kind}:${warning.source?.unitId ?? warning.source?.decisionId ?? warning.message}`;
}

/**
 * Returns warnings not yet shown this session, or null. The server records
 * every warning on the branch store regardless — this filter only prevents
 * the same interruption repeating in-session.
 */
export async function checkEditForWarnings(ctx, sessionId, cwd, filePath, diffContent) {
    if (!ctx.apiKey || !diffContent) return null;
    const branch = ctx.branchOf(cwd || ctx.root) ?? 'HEAD';
    let warnings;
    try {
        const res = await ctx.fetchImpl(`${ctx.apiUrl}/api/sessions/edit-check`, {
            method: 'POST',
            headers: { 'content-type': 'application/json', 'x-api-key': ctx.apiKey },
            body: JSON.stringify({
                repo: ctx.repo, branch, filePath, diff: diffContent, agent: ctx.agent,
            }),
            signal: AbortSignal.timeout(EDIT_CHECK_TIMEOUT_MS),
        });
        if (!res.ok) return null;
        const body = await res.json();
        warnings = Array.isArray(body?.warnings) ? body.warnings : [];
    } catch {
        return null;
    }
    if (warnings.length === 0) return null;

    const warned = loadWarnedKeys(ctx, sessionId);
    const fresh = warnings.filter((w) => !warned.includes(warningKey(w)));
    if (fresh.length === 0) return null;
    saveWarnedKeys(ctx, sessionId, [...warned, ...fresh.map(warningKey)]);
    return fresh;
}

export function formatWarningContext(warnings) {
    return `⚠️ Decispher session memory:\n${warnings.map((w) => `- ${w.message}`).join('\n')}`;
}

// ── Transcript reasoning extraction (Stop hook) ──────────────────────────────

/**
 * Removes fenced code blocks (``` / ~~~) before any sentence work (RD-S2).
 * Diffs are already first-class file_edit captures — code in a reasoning body
 * is duplication and poisons "last 3 sentences". Line-scan with fence-state so
 * nested backticks inside a block never confuse it; an unterminated fence
 * drops the rest of the text (the code tail carries no sentence signal).
 */
export function stripCodeFences(text) {
    const out = [];
    let fence = null;
    for (const line of text.split('\n')) {
        const marker = /^\s*(`{3,}|~{3,})/.exec(line);
        if (fence !== null) {
            if (marker && marker[1][0] === fence[0] && marker[1].length >= fence.length) fence = null;
            continue;
        }
        if (marker) { fence = marker[1]; continue; }
        out.push(line);
    }
    return out.join('\n');
}

/**
 * Trailing CTA/offer paragraphs ("If you want, I can next…", "Would you
 * like…", "Let me know…") — the sign-off, not the substance. Deterministic
 * and deliberately conservative: a false negative is the status quo, a false
 * positive loses a sentence (RD-S2). Only the paragraph's LEAD line is
 * matched, and only from the end of the message inward.
 */
const CLOSING_OFFER_PATTERNS = [
    /^if you (want|like|prefer|need|would like|'d like)\b/i,
    /^would you like\b/i,
    /^do you want\b/i,
    /^want me to\b/i,
    /^let me know\b/i,
    /^shall i\b/i,
    /^i can (also|next|now|then)\b/i,
    /^happy to\b/i,
    /^just say the word\b/i,
];

function isOfferLead(paragraph) {
    const lead = paragraph.split('\n')[0]?.trim() ?? '';
    return CLOSING_OFFER_PATTERNS.some((re) => re.test(lead));
}

function isListParagraph(paragraph) {
    const lines = paragraph.split('\n').map((l) => l.trim()).filter(Boolean);
    return lines.length > 0 && lines.every((l) => /^(\d+[.)]|[-*+•])\s/.test(l));
}

export function stripClosingOffers(text) {
    const paragraphs = text.split(/\n{2,}/);
    while (paragraphs.length > 0) {
        const last = paragraphs[paragraphs.length - 1].trim();
        if (!last) { paragraphs.pop(); continue; }
        if (isOfferLead(last)) { paragraphs.pop(); continue; }
        // A numbered next-step list only reads as an offer when an offer
        // paragraph introduces it — drop the pair, never a bare list.
        if (isListParagraph(last) && paragraphs.length >= 2
            && isOfferLead(paragraphs[paragraphs.length - 2].trim())) {
            paragraphs.pop();
            continue;
        }
        break;
    }
    return paragraphs.join('\n\n').trim();
}

/**
 * When the message carries markdown structure (≥2 headings), the substance is
 * the section map, not the closing sentences — build the tail from each
 * heading plus its lead sentence. Null when the message has no such structure.
 */
function extractStructuredTail(text) {
    const lines = text.split('\n');
    const sections = [];
    for (let i = 0; i < lines.length; i += 1) {
        const heading = /^\s*#{1,6}\s+(.+?)\s*$/.exec(lines[i]);
        if (!heading) continue;
        let lead = '';
        for (let j = i + 1; j < lines.length; j += 1) {
            if (/^\s*#{1,6}\s+/.test(lines[j])) break;
            const t = lines[j].trim().replace(/^([-*+•]|\d+[.)])\s+/, '');
            if (t) { lead = t; break; }
        }
        const sentence = lead.split(/(?<=[.!?])\s+/)[0]?.trim() ?? '';
        sections.push(sentence ? `${heading[1]}: ${sentence}` : heading[1]);
    }
    if (sections.length < 2) return null;
    return sections.join(' · ');
}

/**
 * Per-session transcript cursor (ADR-078 amendment, Fix 3b). The Claude Code
 * transcript file is cumulative — it holds the entire session and is re-read
 * at every Stop. The cursor records how many lines were already processed so
 * each Stop reads only ITS turn: complete per-turn thinking with no arbitrary
 * block cap, bounded volume, and no redundant whole-file re-processing.
 */
function transcriptCursorPath(ctx, sessionId) {
    return path.join(ctx.sessionDir, `transcript-${hashId(sessionId)}.json`);
}

export function loadTranscriptCursor(ctx, sessionId) {
    try {
        const parsed = JSON.parse(fs.readFileSync(transcriptCursorPath(ctx, sessionId), 'utf8'));
        return Number.isInteger(parsed.line) && parsed.line >= 0 ? parsed.line : 0;
    } catch {
        return 0;
    }
}

export function saveTranscriptCursor(ctx, sessionId, line) {
    fs.mkdirSync(ctx.sessionDir, { recursive: true });
    fs.writeFileSync(transcriptCursorPath(ctx, sessionId), JSON.stringify({ line }));
}

/**
 * Reads one turn out of the cumulative transcript: every assistant thinking
 * block after `fromLine`, plus the text blocks of the turn's last assistant
 * message. Returns `{ thinking, finalMessage, lineCount }`; `lineCount` is the
 * new cursor. A transcript shorter than the cursor (rotated/truncated file)
 * resets to a full read rather than silently losing the turn.
 */
export function extractTurn(jsonlText, fromLine = 0) {
    const lines = jsonlText.split('\n');
    const start = fromLine > lines.length ? 0 : fromLine;
    const thinkingBlocks = [];
    let finalMessage = null;
    for (let i = start; i < lines.length; i += 1) {
        const line = lines[i];
        if (!line.trim()) continue;
        let entry;
        try { entry = JSON.parse(line); } catch { continue; }
        const content = entry?.message?.content;
        if (entry?.type !== 'assistant' || !Array.isArray(content)) continue;
        const texts = [];
        for (const block of content) {
            if (block?.type === 'thinking' && typeof block.thinking === 'string') {
                thinkingBlocks.push(block.thinking);
            } else if (block?.type === 'text' && typeof block.text === 'string') {
                texts.push(block.text);
            }
        }
        // The turn's final message is the LAST assistant entry carrying text.
        if (texts.length > 0) finalMessage = texts.join('\n\n');
    }
    return {
        thinking: thinkingBlocks.length > 0 ? thinkingBlocks.join('\n\n') : null,
        finalMessage,
        // The cursor counts only newline-terminated lines. A file ending in
        // '\n' splits into a phantom trailing '' — counting it would shift the
        // cursor one past the last real line and silently drop the first entry
        // of every subsequent turn.
        lineCount: jsonlText.endsWith('\n') ? lines.length - 1 : lines.length,
    };
}

/**
 * Deterministic snippet over one turn's complete thinking — the durable spine
 * (RD-S2 floor rules: strip fences, prefer the heading map, else the last 3
 * sentences; 40-char floor, 1500 cap). Thinking ENDS with its conclusions, so
 * the tail is the right sample.
 */
export function extractThinkingSnippet(text) {
    const stripped = stripCodeFences(text);
    const structured = extractStructuredTail(stripped);
    const tail = structured
        ?? stripped.replace(/\s+/g, ' ').trim().split(/(?<=[.!?])\s+/).slice(-3).join(' ').trim();
    return tail.length >= 40 ? tail.slice(0, 1500) : null;
}

// ── Recording light (statusline) ─────────────────────────────────────────────

const COUNT_ORDER = [
    ['decision', 'decision', 'decisions'],
    ['constraint', 'constraint', 'constraints'],
    ['history', 'rejected attempt', 'rejected attempts'],
    ['convention', 'convention', 'conventions'],
    ['rationale', 'rationale', 'rationales'],
    ['ownership', 'ownership note', 'ownership notes'],
    ['plan', 'plan', 'plans'],
    ['unclassified', 'moment', 'moments'],
];

export function formatCounts(counts) {
    if (!counts || counts.total === 0) return 'capturing';
    const parts = [];
    for (const [type, singular, plural] of COUNT_ORDER) {
        const n = counts.byType?.[type];
        if (!n) continue;
        parts.push(`${n} ${n === 1 ? singular : plural}`);
    }
    return `${parts.join(' · ')} staged`;
}

function formatRemainingShort(ms) {
    const totalMin = Math.max(1, Math.round(ms / 60_000));
    if (totalMin < 60) return `${totalMin}m`;
    const hours = Math.floor(totalMin / 60);
    const minutes = totalMin % 60;
    return minutes === 0 ? `${hours}h` : `${hours}h${minutes}m`;
}

export function renderStatusline(branch, counts, { initialized = true, pause = null, memory = null } = {}) {
    if (!initialized) return '\x1b[2m○ decispher · run npx decispher init\x1b[0m';
    const sep = '\x1b[2m│\x1b[0m';
    if (pause?.paused) {
        // The recording light goes dim but keeps saying "rec" — capture is set
        // up, just held. The countdown reassures the user it resumes on its own.
        const label = pause.indefinite ? 'paused' : `paused ${formatRemainingShort(pause.remainingMs)}`;
        return `\x1b[2m● rec · ${label}\x1b[0m ${sep} ${branch}`;
    }
    const dot = '\x1b[31m●\x1b[0m';
    const memoryPart = memory ? ` ${sep} \x1b[2m${memory}\x1b[0m` : '';
    return `${dot} rec ${sep} ${branch} ${sep} \x1b[2m${formatCounts(counts)}\x1b[0m${memoryPart}`;
}

function statusCachePath(ctx, branch) {
    return path.join(ctx.sessionDir, `status-${hashId(branch)}.json`);
}

async function fetchStagedCounts(ctx, branch, timeoutMs) {
    const url = `${ctx.apiUrl}/api/sessions/units?repo=${encodeURIComponent(ctx.repo)}&branch=${encodeURIComponent(branch)}`;
    const res = await ctx.fetchImpl(url, {
        headers: { 'x-api-key': ctx.apiKey },
        signal: AbortSignal.timeout(timeoutMs),
    });
    if (!res.ok) throw new Error(`units responded ${res.status}`);
    const body = await res.json();
    return body.counts ?? { total: 0, byType: {} };
}

export async function getStagedCountsCached(ctx, branch, { ttlMs = STATUS_CACHE_TTL_MS, timeoutMs = STATUS_TIMEOUT_MS } = {}) {
    const cachePath = statusCachePath(ctx, branch);
    let cached = null;
    try {
        cached = JSON.parse(fs.readFileSync(cachePath, 'utf8'));
    } catch { /* cold cache */ }
    if (cached && Date.now() - cached.at < ttlMs) return cached.counts;
    try {
        const counts = await fetchStagedCounts(ctx, branch, timeoutMs);
        fs.mkdirSync(ctx.sessionDir, { recursive: true });
        fs.writeFileSync(cachePath, JSON.stringify({ at: Date.now(), counts }));
        return counts;
    } catch {
        return cached?.counts ?? { total: 0, byType: {} };
    }
}

export function invalidateStatusCache(ctx, branch) {
    try { fs.unlinkSync(statusCachePath(ctx, branch)); } catch { /* nothing cached */ }
}

// ── Subcommand handlers ──────────────────────────────────────────────────────

function truncate(text, max) {
    return text.length > max ? `${text.slice(0, max)}\n…[truncated]` : text;
}

function toolOutputText(toolResponse) {
    if (typeof toolResponse === 'string') return toolResponse;
    if (toolResponse && typeof toolResponse === 'object') {
        const out = [toolResponse.stdout, toolResponse.stderr, toolResponse.output]
            .filter((v) => typeof v === 'string' && v.length > 0)
            .join('\n');
        if (out) return out;
        try { return JSON.stringify(toolResponse).slice(0, 2_000); } catch { return ''; }
    }
    return '';
}

export async function handleSessionStart(ctx, input) {
    const sessionId = input.session_id ?? `session-${Date.now()}`;
    const cwd = input.cwd ?? ctx.root;
    const branch = ctx.branchOf(cwd) ?? 'HEAD';

    // Paused: skip staging the session boundary, but still serve the briefing —
    // pausing stops capture, never the context you read to start work.
    if (!isCapturePaused()) {
        await enqueueEvents(ctx, [buildEvent(ctx, 'session_start', cwd, {
            sessionId,
            toolUseId: `start:${hashId(sessionId, input.source ?? '')}`,
        })]);
    }

    if (!ctx.apiKey) return null;
    try {
        const url = `${ctx.apiUrl}/api/sessions/briefing?repo=${encodeURIComponent(ctx.repo)}&branch=${encodeURIComponent(branch)}`;
        const res = await ctx.fetchImpl(url, {
            headers: { 'x-api-key': ctx.apiKey },
            signal: AbortSignal.timeout(BRIEFING_TIMEOUT_MS),
        });
        if (!res.ok) return null;
        const body = await res.json();
        const briefing = body?.briefing;
        // Keep the resolved-project cache in step with the server so the next
        // invocation picks the matching project-scoped key after a remap.
        if (briefing && briefing.projectId !== undefined && briefing.projectId !== ctx.projectId) {
            try { writeCachedProjectId(ctx.sessionDir, briefing.projectId); } catch { /* best-effort cache */ }
        }
        const markdown = briefing?.markdown;
        if (!markdown) return null;
        return {
            hookSpecificOutput: {
                hookEventName: 'SessionStart',
                additionalContext: markdown,
            },
        };
    } catch {
        return null;
    }
}

export async function handleUserPrompt(ctx, input) {
    const prompt = typeof input.prompt === 'string' ? input.prompt : '';
    if (!prompt.trim()) return null;
    const sessionId = input.session_id ?? 'unknown-session';

    // Capture is pausable; memory injection is not part of capture, so it keeps
    // serving while paused (the same rule the session-start briefing follows).
    if (!isCapturePaused()) {
        const redacted = redactText(truncate(prompt, MAX_PROMPT_CHARS));
        await enqueueEvents(ctx, [buildEvent(ctx, 'user_prompt', input.cwd, {
            sessionId,
            toolUseId: `prompt:${hashId(sessionId, prompt)}`,
            userPrompt: redacted,
        })]);
    }

    const resolved = await resolveMemoryForPrompt(ctx, sessionId, prompt);
    const context = applyResolvedMemory(ctx, sessionId, resolved);
    if (!context) return null;

    return {
        hookSpecificOutput: {
            hookEventName: 'UserPromptSubmit',
            additionalContext: context,
        },
    };
}

export async function handlePostTool(ctx, input) {
    if (isCapturePaused()) return null;
    const sessionId = input.session_id ?? 'unknown-session';
    const toolName = input.tool_name ?? '';
    const toolInput = input.tool_input ?? {};
    const baseId = input.tool_use_id ?? hashId(sessionId, toolName, JSON.stringify(toolInput));
    const events = [];

    if (toolName === 'Bash') {
        const command = redactText(String(toolInput.command ?? ''));
        if (!command) return null;
        const output = redactText(truncate(toolOutputText(input.tool_response), MAX_TERMINAL_OUTPUT_CHARS));
        events.push(buildEvent(ctx, 'terminal_command', input.cwd, {
            sessionId,
            toolUseId: `bash:${baseId}`,
            terminal: { command, output },
        }));
    } else if (toolName === 'Edit' || toolName === 'Write' || toolName === 'MultiEdit') {
        const filePath = String(toolInput.file_path ?? '');
        if (!filePath) return null;
        const pairs = toolName === 'MultiEdit' && Array.isArray(toolInput.edits)
            ? toolInput.edits.map((e) => [String(e.old_string ?? ''), String(e.new_string ?? '')])
            : toolName === 'Write'
                ? [['', String(toolInput.content ?? '')]]
                : [[String(toolInput.old_string ?? ''), String(toolInput.new_string ?? '')]];
        events.push(...stageEditPairs(ctx, sessionId, input.cwd, filePath, pairs, `edit:${baseId}`));
    } else if (toolName === 'AskUserQuestion') {
        // The agent asked, the human answered — the steering moment that shaped
        // the work, captured nowhere else. One clarification per answered question.
        events.push(...stageClarifications(ctx, sessionId, input.cwd, baseId, toolInput, input.tool_response));
        if (events.length === 0) return null;
    } else {
        return null;
    }

    const flushed = await enqueueEvents(ctx, events);

    // SR-S8 — déjà vu / drift check on the staged edit, surfaced back into the
    // agent's context at the moment of repetition. Skipped while offline
    // (buffered events remaining) so a dead network never stacks two timeouts.
    const edit = events.filter((e) => e.kind === 'file_edit' && e.diff).pop();
    if (edit && flushed?.remaining === 0) {
        const fresh = await checkEditForWarnings(ctx, sessionId, input.cwd, edit.diff.filePath, edit.diff.content);
        if (fresh) {
            return {
                hookSpecificOutput: {
                    hookEventName: 'PostToolUse',
                    additionalContext: formatWarningContext(fresh),
                },
            };
        }
    }
    return null;
}

/**
 * Shared by Claude Code PostToolUse(Edit|Write|MultiEdit) and Cursor
 * afterFileEdit: never-capture guard, rejected-attempt detection over the
 * session's edit history, per-edit diff assembly. Returns the events to stage.
 */
export function stageEditPairs(ctx, sessionId, cwd, filePath, pairs, editToolUseId) {
    if (isNeverCapturePath(filePath)) {
        // Path only — content from sensitive files is never read (ADR-052).
        return [buildEvent(ctx, 'file_edit', cwd, {
            sessionId,
            toolUseId: editToolUseId,
            body: `Edited ${filePath} (content not captured: sensitive path)`,
        })];
    }

    const events = [];
    const state = loadEditState(ctx, sessionId);
    let diffContent = '';
    for (const [oldStr, newStr] of pairs) {
        const rejected = detectRejectedAttempt(state[filePath] ?? [], oldStr, newStr);
        if (rejected) {
            events.push(buildEvent(ctx, 'rejected_attempt', cwd, {
                sessionId,
                toolUseId: `rejected:${hashId(filePath, rejected.oldHash, rejected.newHash)}`,
                body: `Tried and reverted a change to ${filePath}`,
                diff: {
                    filePath,
                    content: redactText(unifiedDiff(filePath, rejected.old, rejected.new)),
                    op: 'revert',
                },
            }));
        }
        recordEdit(state, filePath, oldStr, newStr);
        diffContent += (diffContent ? '\n' : '') + unifiedDiff(filePath, oldStr, newStr);
    }
    saveEditState(ctx, sessionId, state);

    events.push(buildEvent(ctx, 'file_edit', cwd, {
        sessionId,
        toolUseId: editToolUseId,
        diff: {
            filePath,
            content: redactText(truncate(diffContent, MAX_DIFF_CHARS)),
            op: 'edit',
        },
    }));
    return events;
}

/**
 * Pairs each AskUserQuestion question with the human's chosen answer. The
 * Claude Code tool_response carries `answers` keyed by question text; an
 * index-aligned fallback covers shape drift. A question with no answer is
 * dropped (the answer is the durable steering signal, not the question).
 */
export function stageClarifications(ctx, sessionId, cwd, baseId, toolInput, toolResponse) {
    const questions = Array.isArray(toolInput?.questions) ? toolInput.questions : [];
    const answers = (toolResponse && typeof toolResponse === 'object' && toolResponse.answers
        && typeof toolResponse.answers === 'object') ? toolResponse.answers : null;
    const answerValues = answers ? Object.values(answers).filter((v) => typeof v === 'string') : [];

    const events = [];
    questions.forEach((q, i) => {
        const question = typeof q?.question === 'string' ? q.question : '';
        if (!question) return;
        const answer = (answers && typeof answers[question] === 'string')
            ? answers[question]
            : (answerValues[i] ?? '');
        if (!answer.trim()) return;
        events.push(buildEvent(ctx, 'clarification', cwd, {
            sessionId,
            toolUseId: `clarify:${hashId(baseId, question, answer)}`,
            agentQuestion: redactText(truncate(question, MAX_PROMPT_CHARS)),
            userAnswer: redactText(truncate(answer, MAX_PROMPT_CHARS)),
        }));
    });
    return events;
}

/**
 * Claude Code Stop — the two-layer treatment Codex already gets (Fix 3 + 3b).
 * The transcript cursor bounds the read to THIS turn; from it we stage:
 *
 *   1. The turn's COMPLETE thinking (no block cap) — `body` is the
 *      deterministic snippet, `reasoning` the full redacted thinking as 64k
 *      encrypted provenance. Thinking holds the roads not taken.
 *   2. The turn's final assistant message — the same two-layer event Codex
 *      emits (`extractMessageTail` body + full redacted message provenance).
 *
 * Separate content-hashed toolUseIds, so the two never collide and re-sends
 * dedup server-side. Redact-then-truncate, never the reverse (D7).
 */
export async function handleStop(ctx, input) {
    const sessionId = input.session_id ?? 'unknown-session';
    const events = [];
    if (!isCapturePaused() && typeof input.transcript_path === 'string' && fs.existsSync(input.transcript_path)) {
        try {
            const transcript = fs.readFileSync(input.transcript_path, 'utf8');
            const cursor = loadTranscriptCursor(ctx, sessionId);
            const turn = extractTurn(transcript, cursor);

            if (turn.thinking) {
                const snippet = extractThinkingSnippet(turn.thinking);
                if (snippet) {
                    events.push(buildEvent(ctx, 'reasoning', input.cwd, {
                        sessionId,
                        toolUseId: `reasoning:${hashId(sessionId, 'thinking', turn.thinking)}`,
                        body: redactText(snippet),
                        reasoning: redactText(turn.thinking).slice(0, MAX_REASONING_PROVENANCE_CHARS),
                    }));
                }
            }
            if (turn.finalMessage) {
                const event = buildFinalMessageReasoningEvent(ctx, input.cwd, sessionId, turn.finalMessage);
                if (event) events.push(event);
            }
            try { saveTranscriptCursor(ctx, sessionId, turn.lineCount); } catch { /* cursor is best-effort */ }
        } catch { /* transcript unreadable — skip, never block */ }
    }
    await enqueueEvents(ctx, events);
    await flushBuffer(ctx);
}

export async function handleSessionEnd(ctx, input) {
    if (isCapturePaused()) { await flushBuffer(ctx); return; }
    const sessionId = input.session_id ?? 'unknown-session';
    await enqueueEvents(ctx, [buildEvent(ctx, 'session_end', input.cwd, {
        sessionId,
        toolUseId: `end:${hashId(sessionId)}`,
    })]);
    await flushBuffer(ctx);
}

// ── Cursor hooks (.cursor/hooks.json) ────────────────────────────────────────
// Cursor exposes no transcript — reasoning arrives via the rules-driven
// session_record MCP self-report. Hooks here capture only the evidence floor
// (edits, shell commands, session boundary). sessionId = conversation_id, so
// hook events and the agent's self-reports correlate into the same session;
// dedup across the two paths is by sourceEventId (hooks use content-stable
// `cedit:`/`cshell:` ids, self-reports hash type|statement server-side).

function cursorSessionId(input) {
    // Most Cursor hooks carry `conversation_id`; `sessionStart` carries
    // `session_id`. Same session either way, so the memory non-repeat window
    // and the capture ids agree.
    if (typeof input.conversation_id === 'string' && input.conversation_id) return input.conversation_id;
    if (typeof input.session_id === 'string' && input.session_id) return input.session_id;
    return 'cursor-session';
}

function cursorCwd(input, ctx) {
    if (typeof input.cwd === 'string' && input.cwd) return input.cwd;
    if (Array.isArray(input.workspace_roots) && typeof input.workspace_roots[0] === 'string') {
        return input.workspace_roots[0];
    }
    return ctx.root;
}

export async function handleCursorAfterEdit(ctx, input) {
    if (isCapturePaused()) return;
    const filePath = typeof input.file_path === 'string' ? input.file_path : '';
    if (!filePath) return;
    const sessionId = cursorSessionId(input);
    const pairs = Array.isArray(input.edits)
        ? input.edits.map((e) => [String(e?.old_string ?? ''), String(e?.new_string ?? '')])
        : [];
    if (pairs.length === 0) return;

    // Content-stable id: a redelivered hook event upserts, never duplicates.
    const editId = `cedit:${hashId(filePath, ...pairs.map(([o, n]) => hashId(o, n)))}`;
    const cwd = cursorCwd(input, ctx);
    const events = stageEditPairs(ctx, sessionId, cwd, filePath, pairs, editId);
    const flushed = await enqueueEvents(ctx, events);

    // SR-S8 — run the déjà vu / drift check so the warning lands on the branch
    // store (Cursor hooks can't inject context; the store + receipt surface it).
    const edit = events.filter((e) => e.kind === 'file_edit' && e.diff).pop();
    if (edit && flushed?.remaining === 0) {
        await checkEditForWarnings(ctx, sessionId, cwd, edit.diff.filePath, edit.diff.content);
    }
}

export async function handleCursorBeforeShell(ctx, input) {
    if (isCapturePaused()) return;
    const command = redactText(String(input.command ?? ''));
    if (!command) return;
    const sessionId = cursorSessionId(input);
    const cwd = cursorCwd(input, ctx);
    // Buffer only — this hook gates the shell command, so it must never wait
    // on the network. The buffer drains on the next afterFileEdit/stop.
    appendToBuffer(ctx, [buildEvent(ctx, 'terminal_command', cwd, {
        sessionId,
        toolUseId: `cshell:${hashId(sessionId, command, String(input.generation_id ?? ''))}`,
        terminal: { command, output: '' },
    })]);
}

/**
 * Cursor's memory seam (ADR-075 §4.4). `beforeSubmitPrompt` sees the prompt but
 * may only answer `continue`/`user_message`, so it cannot inject; `sessionStart`
 * and `postToolUse` are Cursor's only context-injecting hooks and only
 * `sessionStart` lands before the agent acts.
 *
 * No task text exists at session start, so this serves the mandatory company
 * overlay plus any set a set key attached — rungs 5 and 1. The embedding rungs
 * stay dark here by construction; Cursor picks up task-relevant memory
 * mid-session through the MCP sidecar instead.
 *
 * Injection is not capture, so it keeps serving while capture is paused — the
 * same rule handleUserPrompt follows.
 */
export async function handleCursorSessionStart(ctx, input) {
    const sessionId = cursorSessionId(input);
    const resolved = await resolveMemoryForPrompt(ctx, sessionId, '', 'briefing');
    const context = applyResolvedMemory(ctx, sessionId, resolved);
    if (!context) return null;
    // Cursor's own output shape — snake_case, top level. NOT the Claude
    // `hookSpecificOutput.additionalContext` envelope the other three use.
    return { additional_context: context };
}

export async function handleCursorStop(ctx, input) {
    if (isCapturePaused()) { await flushBuffer(ctx); return; }
    const sessionId = cursorSessionId(input);
    await enqueueEvents(ctx, [buildEvent(ctx, 'session_end', cursorCwd(input, ctx), {
        sessionId,
        toolUseId: `end:${hashId(sessionId, String(input.status ?? ''))}`,
    })]);
    await flushBuffer(ctx);
}

// ── Codex hooks (.codex/hooks.json) ──────────────────────────────────────────
// Codex's hook payloads are Claude-Code-shaped (session_id/cwd/tool_name/
// tool_input/tool_response, hookSpecificOutput.additionalContext), so
// SessionStart and UserPromptSubmit reuse the Claude handlers with
// agent='codex'. The differences live here:
//  - File edits arrive as apply_patch envelopes in tool_input.command, on two
//    paths: native apply_patch events (tool_name "apply_patch", since
//    openai/codex#18391, April 2026) and shell-applied `apply_patch <<EOF`
//    heredocs (tool_name "Bash", the only path on older Codex versions).
//    Both parse back into per-file edits so diffs, rejected-attempt
//    detection, and the SR-S8 déjà vu check all keep working.
//  - Stop is turn-end, not session-end (Codex has no SessionEnd): flush +
//    stage a reasoning snippet from the documented last_assistant_message.

/**
 * Parses an apply_patch envelope (`*** Begin Patch` … `*** End Patch`) out of
 * a shell command. Returns per-file sections `{ op, path, oldStr, newStr }`,
 * or [] when the command carries no envelope. Old/new are reconstructed from
 * the hunk's -/+ lines — approximate but deterministic, which is all the
 * rejected-attempt hash pairing needs.
 */
export function parseApplyPatchEnvelope(command) {
    const begin = command.indexOf('*** Begin Patch');
    const end = command.indexOf('*** End Patch');
    if (begin === -1 || end === -1 || end <= begin) return [];

    const files = [];
    let current = null;
    const flush = () => {
        if (!current) return;
        files.push({
            op: current.op,
            path: current.path,
            oldStr: current.oldLines.join('\n'),
            newStr: current.newLines.join('\n'),
        });
        current = null;
    };

    for (const line of command.slice(begin, end).split('\n')) {
        const header = /^\*\*\* (Add|Update|Delete) File: (.+)$/.exec(line.trim());
        if (header) {
            flush();
            const op = header[1].toLowerCase();
            current = { op, path: header[2].trim(), oldLines: [], newLines: [] };
            continue;
        }
        if (!current) continue;
        if (line.startsWith('*** ')) continue; // Begin Patch / Move to / etc.
        if (line.startsWith('+')) current.newLines.push(line.slice(1));
        else if (line.startsWith('-')) current.oldLines.push(line.slice(1));
        else if (line.startsWith('@@')) { /* hunk marker — positional only */ }
        else if (line.trim() !== '') {
            // Context line — present on both sides.
            const ctxLine = line.startsWith(' ') ? line.slice(1) : line;
            current.oldLines.push(ctxLine);
            current.newLines.push(ctxLine);
        }
    }
    flush();
    return files;
}

const CODEX_SHELL_TOOLS = ['Bash', 'shell', 'local_shell'];
const CODEX_PATCH_TOOLS = ['apply_patch', 'Edit', 'Write'];

export async function handleCodexPostTool(ctx, input) {
    if (isCapturePaused()) return null;
    const sessionId = input.session_id ?? 'unknown-session';
    const toolName = input.tool_name ?? '';
    const isShell = CODEX_SHELL_TOOLS.includes(toolName);
    const isPatch = CODEX_PATCH_TOOLS.includes(toolName);
    if (!isShell && !isPatch) return null;
    const toolInput = input.tool_input ?? {};
    // Codex shell input is a string or an argv array depending on version; the
    // native apply_patch event carries the raw patch body in the same field.
    const rawCommand = Array.isArray(toolInput.command)
        ? toolInput.command.map(String).join(' ')
        : String(toolInput.command ?? '');
    if (!rawCommand.trim()) return null;
    const baseId = input.tool_use_id ?? hashId(sessionId, toolName, rawCommand);

    const events = [];
    const patchedFiles = parseApplyPatchEnvelope(rawCommand);
    if (isPatch && patchedFiles.length === 0) return null; // malformed envelope — nothing to stage
    if (patchedFiles.length > 0) {
        // The shell command IS the edit — stage file edits, not terminal noise.
        for (const file of patchedFiles) {
            if (file.op === 'delete') {
                events.push(buildEvent(ctx, 'file_edit', input.cwd, {
                    sessionId,
                    toolUseId: `edit:${hashId(baseId, file.path, 'delete')}`,
                    body: `Deleted ${file.path}`,
                }));
                continue;
            }
            const pairs = [[file.op === 'add' ? '' : file.oldStr, file.newStr]];
            events.push(...stageEditPairs(
                ctx, sessionId, input.cwd, file.path, pairs,
                `edit:${hashId(baseId, file.path)}`,
            ));
        }
    } else {
        events.push(buildEvent(ctx, 'terminal_command', input.cwd, {
            sessionId,
            toolUseId: `bash:${baseId}`,
            terminal: {
                command: redactText(rawCommand),
                output: redactText(truncate(toolOutputText(input.tool_response), MAX_TERMINAL_OUTPUT_CHARS)),
            },
        }));
    }

    const flushed = await enqueueEvents(ctx, events);

    // SR-S8 déjà vu / drift — Codex supports PostToolUse additionalContext, so
    // the warning lands in the model's context exactly like Claude Code.
    const edit = events.filter((e) => e.kind === 'file_edit' && e.diff).pop();
    if (edit && flushed?.remaining === 0) {
        const fresh = await checkEditForWarnings(ctx, sessionId, input.cwd, edit.diff.filePath, edit.diff.content);
        if (fresh) {
            return {
                hookSpecificOutput: {
                    hookEventName: 'PostToolUse',
                    additionalContext: formatWarningContext(fresh),
                },
            };
        }
    }
    return null;
}

/**
 * Codex Stop fires at every turn end (there is no SessionEnd). Stage the smart
 * snippet of the final assistant message — the documented
 * `last_assistant_message` field, stable across Codex versions, unlike the
 * undocumented rollout transcript format — then flush.
 *
 * RD-S2 deterministic floor: strip fences → strip closing offers → prefer the
 * heading/lead-sentence map when the message has markdown sections, else the
 * last 3 sentences. The 40-char floor and 1500 cap are unchanged. A message
 * that was ALL sign-off strips to nothing and stages no event.
 */
export function extractMessageTail(text) {
    const stripped = stripClosingOffers(stripCodeFences(text));
    const structured = extractStructuredTail(stripped);
    const tail = structured ?? stripped.replace(/\s+/g, ' ').trim().split(/(?<=[.!?])\s+/).slice(-3).join(' ').trim();
    return tail.length >= 40 ? tail.slice(0, 1500) : null;
}

/**
 * RD (ADR-078, D1) — the two-layer reasoning event for final-message agents:
 * `body` is the deterministic smart snippet (the durable spine everyone gets),
 * `reasoning` is the FULL redacted message as encrypted, purge-windowed
 * provenance so distillation stays possible any time later. Redact-then-
 * truncate, never the reverse (D7).
 */
function buildFinalMessageReasoningEvent(ctx, cwd, sessionId, message) {
    const tail = extractMessageTail(message);
    if (!tail) return null;
    return buildEvent(ctx, 'reasoning', cwd, {
        sessionId,
        toolUseId: `reasoning:${hashId(sessionId, tail)}`,
        body: redactText(tail),
        reasoning: redactText(message).slice(0, MAX_REASONING_PROVENANCE_CHARS),
    });
}

export async function handleCodexStop(ctx, input) {
    const events = [];
    if (!isCapturePaused() && typeof input.last_assistant_message === 'string') {
        const sessionId = input.session_id ?? 'unknown-session';
        const event = buildFinalMessageReasoningEvent(ctx, input.cwd, sessionId, input.last_assistant_message);
        if (event) events.push(event);
    }
    await enqueueEvents(ctx, events);
    await flushBuffer(ctx);
}

// ── Grok Build hooks (.grok/hooks/decispher.json) ────────────────────────────
// Grok's hook contract is Claude-shaped in structure but differs in detail:
//  - Payload fields are camelCase (sessionId/toolName/toolInput) and tool
//    events carry Grok's internal tool names (run_terminal_cmd,
//    search_replace). normalizeGrokInput maps the payload onto the
//    snake_case fields the shared handlers read.
//  - Passive-event stdout is IGNORED (only PreToolUse can answer), so there
//    is no SessionStart briefing injection and no PostToolUse déjà vu
//    injection — the briefing arrives via store_read (AGENTS.md instructs
//    it) and edit-check warnings land on the branch store, like Cursor.
//  - Grok ALSO reads .claude/settings.json and .cursor/hooks.json hooks and
//    sets GROK_HOOK_EVENT on every hook process. main() uses that to keep a
//    Grok session single-captured: when this repo carries our dedicated
//    grok hooks file the compat invocations exit; without it they run
//    re-tagged agent='grok' so a claude-/cursor-only repo still captures.

export function isGrokHookProcess(env = process.env) {
    return typeof env.GROK_HOOK_EVENT === 'string' && env.GROK_HOOK_EVENT.length > 0;
}

export function hasGrokHooksFile(root) {
    return fs.existsSync(path.join(root, '.grok', 'hooks', 'decispher.json'));
}

/**
 * Maps a Grok payload onto the snake_case fields the shared handlers read.
 * Additive — camelCase originals stay, existing snake_case fields win, so
 * normalizing a payload that is already Claude-shaped is a no-op.
 */
export function normalizeGrokInput(input) {
    const out = { ...input };
    if (out.session_id === undefined && typeof out.sessionId === 'string') out.session_id = out.sessionId;
    if (out.cwd === undefined && typeof out.workspaceRoot === 'string') out.cwd = out.workspaceRoot;
    if (out.tool_name === undefined && typeof out.toolName === 'string') out.tool_name = out.toolName;
    if (out.tool_input === undefined && out.toolInput !== undefined) out.tool_input = out.toolInput;
    if (out.tool_response === undefined && out.toolResponse !== undefined) out.tool_response = out.toolResponse;
    if (out.tool_use_id === undefined && typeof out.toolUseId === 'string') out.tool_use_id = out.toolUseId;
    return out;
}

const GROK_SHELL_TOOLS = ['run_terminal_cmd', 'Bash', 'bash', 'shell'];
const GROK_EDIT_TOOLS = ['search_replace', 'Edit', 'edit_file', 'MultiEdit'];
const GROK_WRITE_TOOLS = ['write_file', 'create_file', 'Write'];

function firstString(obj, keys) {
    for (const key of keys) {
        if (typeof obj[key] === 'string' && obj[key]) return obj[key];
    }
    return '';
}

export async function handleGrokPostTool(ctx, input) {
    if (isCapturePaused()) return;
    const sessionId = input.session_id ?? 'unknown-session';
    const toolName = input.tool_name ?? '';
    const toolInput = input.tool_input ?? {};
    const baseId = input.tool_use_id ?? hashId(sessionId, toolName, JSON.stringify(toolInput));
    const events = [];

    if (GROK_SHELL_TOOLS.includes(toolName)) {
        const rawCommand = Array.isArray(toolInput.command)
            ? toolInput.command.map(String).join(' ')
            : String(toolInput.command ?? '');
        if (!rawCommand.trim()) return;
        events.push(buildEvent(ctx, 'terminal_command', input.cwd, {
            sessionId,
            toolUseId: `bash:${baseId}`,
            terminal: {
                command: redactText(rawCommand),
                output: redactText(truncate(toolOutputText(input.tool_response), MAX_TERMINAL_OUTPUT_CHARS)),
            },
        }));
    } else if (GROK_EDIT_TOOLS.includes(toolName) || GROK_WRITE_TOOLS.includes(toolName)) {
        // Field names are tolerant across Grok versions — the docs pin the
        // envelope (toolName/toolInput) but not the per-tool input schema.
        const filePath = firstString(toolInput, ['file_path', 'filePath', 'path', 'target_file']);
        if (!filePath) return;
        const oldStr = firstString(toolInput, ['old_string', 'oldString', 'old_str']);
        const newStr = GROK_WRITE_TOOLS.includes(toolName)
            ? firstString(toolInput, ['content', 'contents', 'file_text', 'text'])
            : firstString(toolInput, ['new_string', 'newString', 'new_str', 'replacement']);
        if (!oldStr && !newStr) {
            // Evidence floor: the edit happened even when this Grok version
            // does not expose the change content through the hook payload.
            events.push(buildEvent(ctx, 'file_edit', input.cwd, {
                sessionId,
                toolUseId: `edit:${baseId}`,
                body: `Edited ${filePath} (content not exposed by the Grok hook payload)`,
            }));
        } else {
            events.push(...stageEditPairs(ctx, sessionId, input.cwd, filePath, [[oldStr, newStr]], `edit:${baseId}`));
        }
    } else {
        return;
    }

    const flushed = await enqueueEvents(ctx, events);

    // SR-S8 déjà vu / drift — Grok ignores passive-event stdout, so no
    // additionalContext round-trip; the warning still lands on the branch
    // store (timeline + receipt), the same serving contract as Cursor.
    const edit = events.filter((e) => e.kind === 'file_edit' && e.diff).pop();
    if (edit && flushed?.remaining === 0) {
        await checkEditForWarnings(ctx, sessionId, input.cwd, edit.diff.filePath, edit.diff.content);
    }
}

/**
 * Grok Stop fires at turn end (SessionEnd covers the session boundary).
 * Stage a reasoning snippet when the payload exposes the final assistant
 * message under the Codex-style field, then flush — tolerant: absent field
 * just means flush-only. Same RD-S2 two-layer event as Codex (body = smart
 * snippet, reasoning = full redacted message as provenance).
 */
export async function handleGrokStop(ctx, input) {
    const events = [];
    if (!isCapturePaused() && typeof input.last_assistant_message === 'string') {
        const sessionId = input.session_id ?? 'unknown-session';
        const event = buildFinalMessageReasoningEvent(ctx, input.cwd, sessionId, input.last_assistant_message);
        if (event) events.push(event);
    }
    await enqueueEvents(ctx, events);
    await flushBuffer(ctx);
}

export async function handlePostCheckout(ctx, argv) {
    const [prevRef = '', nextRef = '', flag = ''] = argv;
    if (flag !== '1') return; // file checkout, not a branch switch
    const branch = ctx.branchOf(ctx.root) ?? 'HEAD';
    invalidateStatusCache(ctx, branch);
    if (isCapturePaused()) return;
    await enqueueEvents(ctx, [buildEvent(ctx, 'branch_checkout', ctx.root, {
        sessionId: `git:${branch}`,
        toolUseId: `checkout:${hashId(prevRef, nextRef, branch)}`,
    })]);
}


// ── Decispher Memory (ADR-075 §5.3) ──────────────────────────────────────────
// The prompt hook is an owned seam: it ATTACHES context, it never rewrites the
// prompt (anti-dump law #3). High-band set matches attach silently; mid-band
// matches only suggest, so the user stays in control of what rides along.

const MEMORY_TIMEOUT_MS = 2500;

function memoryStatePath(ctx, sessionId) {
    return path.join(ctx.sessionDir, `memory-${hashId(sessionId)}.json`);
}

function readMemoryState(ctx, sessionId) {
    try {
        return JSON.parse(fs.readFileSync(memoryStatePath(ctx, sessionId), 'utf8'));
    } catch {
        return null;
    }
}

function writeMemoryState(ctx, sessionId, state) {
    try {
        fs.mkdirSync(ctx.sessionDir, { recursive: true });
        fs.writeFileSync(memoryStatePath(ctx, sessionId), JSON.stringify(state));
    } catch {
        // Statusline decoration only: never fail a prompt over a cache write.
    }
}

/**
 * Resolves memory for one prompt. Returns null on anything unexpected: the
 * plane being off (403), an older API (404), a timeout, or a malformed body.
 * A prompt must never be blocked or delayed by memory.
 */
export async function resolveMemoryForPrompt(ctx, sessionId, prompt, surface = 'resolve') {
    if (!ctx.apiKey) return null;
    try {
        const taskText = typeof prompt === 'string' ? prompt.trim() : '';
        const res = await ctx.fetchImpl(`${ctx.apiUrl}/api/memory/resolve`, {
            method: 'POST',
            headers: {
                'content-type': 'application/json',
                'x-api-key': ctx.apiKey,
                // ADR-077 precedence: with this header the api key stays the
                // tenant credential and the principal is named explicitly, so a
                // shared/CI key still serves the right person's memory.
                ...(ctx.identityKey ? { 'x-identity-key': ctx.identityKey } : {}),
            },
            body: JSON.stringify({
                sessionKey: `cli:${sessionId}`,
                surface,
                // Omitted rather than sent empty: a seam with no task text
                // (Cursor's sessionStart) resolves the mandatory overlay and
                // key-attached sets, and the absent field is what tells the
                // ladder to skip the embedding rungs instead of embedding ''.
                ...(taskText ? { taskText } : {}),
            }),
            signal: AbortSignal.timeout(MEMORY_TIMEOUT_MS),
        });
        if (!res.ok) return null;
        const body = await res.json();
        return body?.data ?? body ?? null;
    } catch {
        return null;
    }
}

/**
 * Shared tail of every memory seam: record the statusline state and render the
 * blocks. Returns null when there is nothing to inject, so each caller only has
 * to wrap the string in its own host's output shape.
 */
export function applyResolvedMemory(ctx, sessionId, resolved) {
    if (!resolved) return null;
    const served = Array.isArray(resolved.memories) ? resolved.memories.length : 0;
    const suggestions = Array.isArray(resolved.suggestedSets) ? resolved.suggestedSets : [];
    const attached = Array.isArray(resolved.attachedSets) ? resolved.attachedSets : [];

    writeMemoryState(ctx, sessionId, {
        served,
        activeSet: attached[0]?.slug ?? null,
        memberCount: served,
    });

    const blocks = [];
    if (resolved.payload) blocks.push(resolved.payload);
    // A pinned set past the per-session ceiling is thinned, not silently
    // dropped: name the count so the user knows more is pinned than shown.
    const truncated = Number(resolved?.receipt?.truncated ?? 0);
    if (resolved.payload && truncated > 0) {
        blocks.push(`[decispher memory] ${truncated} more pinned memor${truncated === 1 ? 'y' : 'ies'} not shown this session (over the per-session cap).`);
    }
    // Mid band only suggests: naming the set teaches the @memory: convention
    // without silently pulling memories the user did not ask for.
    if (suggestions.length > 0) {
        const names = suggestions.slice(0, 2).map((s) => `@memory:${s.slug}`).join(' or ');
        blocks.push(`[decispher memory] Related set available. Add ${names} to your prompt to attach it.`);
    }
    return blocks.length > 0 ? blocks.join('\n') : null;
}

/** The `mem:` statusline segment text, or null when there is nothing to say. */
export function memorySegment(state) {
    if (!state) return null;
    if (state.activeSet) {
        return state.memberCount > 0
            ? `mem: ${state.activeSet} (${state.memberCount})`
            : `mem: ${state.activeSet}`;
    }
    return state.served > 0 ? `mem: auto (${state.served})` : null;
}

export async function handleStatusline(ctx, input) {
    const cwd = input?.workspace?.current_dir ?? ctx.root;
    const branch = ctx.branchOf(cwd) ?? 'HEAD';
    const pause = readPauseView();
    if (pause.paused) return renderStatusline(branch, null, { pause });
    if (!ctx.apiKey) return renderStatusline(branch, null, { initialized: false });
    const counts = await getStagedCountsCached(ctx, branch);
    const sessionId = input?.session_id ?? null;
    const memory = sessionId ? memorySegment(readMemoryState(ctx, sessionId)) : null;
    return renderStatusline(branch, counts, memory ? { memory } : {});
}

// ── Entry point ──────────────────────────────────────────────────────────────

async function readStdinJson() {
    try {
        let raw = '';
        for await (const chunk of process.stdin) raw += chunk;
        return raw.trim() ? JSON.parse(raw) : {};
    } catch {
        return {};
    }
}

const CURSOR_ALLOW = JSON.stringify({ permission: 'allow' });

/** Returns the text to print on stdout (hook JSON or statusline), or null. */
async function main() {
    const [subcommand, ...rest] = process.argv.slice(2);
    let input = subcommand === 'post-checkout' ? {} : await readStdinJson();
    const isCursor = subcommand?.startsWith('cursor-') ?? false;
    const isCodex = subcommand?.startsWith('codex-') ?? false;
    const isGrok = subcommand?.startsWith('grok-') ?? false;
    // GROK_HOOK_EVENT marks every hook process Grok spawns — including the
    // ones it fires through the Claude/Cursor hook files it also reads. The
    // runtime, not the invoking config file, decides the attribution.
    const grokSession = isGrokHookProcess();
    if (grokSession || isGrok) input = normalizeGrokInput(input);
    const startDir = isCursor
        ? (input.cwd ?? (Array.isArray(input.workspace_roots) ? input.workspace_roots[0] : undefined) ?? process.cwd())
        : (input.cwd ?? process.cwd());
    const agent = (isGrok || grokSession) ? 'grok' : isCursor ? 'cursor' : isCodex ? 'codex' : null;
    const ctx = createContext(startDir, agent ? { agent } : {});

    if (subcommand === 'statusline') {
        return ctx
            ? handleStatusline(ctx, input)
            : '\x1b[2m○ decispher · run npx decispher init\x1b[0m';
    }
    // Double-capture guard: under a Grok session the dedicated grok-* entries
    // own capture whenever this repo carries our .grok hooks file — the
    // compat invocations (Claude/Cursor/Codex files, also read by Grok) exit.
    // Without the file (repo wired for another agent only) the compat
    // invocation proceeds, already re-tagged agent='grok' above.
    if (grokSession && !isGrok && subcommand !== 'post-checkout' && ctx && hasGrokHooksFile(ctx.root)) {
        return null;
    }
    if (subcommand === 'cursor-before-shell') {
        // This hook gates the user's shell command — answer allow no matter
        // what (uninitialized repo, capture failure, anything).
        if (ctx) { try { await handleCursorBeforeShell(ctx, input); } catch { /* never block */ } }
        return CURSOR_ALLOW;
    }
    if (!ctx) return null; // not initialized — never interfere with the session

    switch (subcommand) {
        case 'session-start': {
            const out = await handleSessionStart(ctx, input);
            return out ? JSON.stringify(out) : null;
        }
        case 'user-prompt': {
            const out = await handleUserPrompt(ctx, input);
            return out ? JSON.stringify(out) : null;
        }
        case 'post-tool': {
            // A Grok session reaching the Claude entry (no dedicated grok
            // wiring) carries Grok tool names — route to the Grok handler.
            if (grokSession) { await handleGrokPostTool(ctx, input); return null; }
            const out = await handlePostTool(ctx, input);
            return out ? JSON.stringify(out) : null;
        }
        case 'stop': await handleStop(ctx, input); return null;
        case 'session-end': await handleSessionEnd(ctx, input); return null;
        case 'cursor-session-start': {
            const out = await handleCursorSessionStart(ctx, input);
            return out ? JSON.stringify(out) : null;
        }
        case 'cursor-after-edit': await handleCursorAfterEdit(ctx, input); return null;
        case 'cursor-stop': await handleCursorStop(ctx, input); return null;
        // Codex payloads are Claude-shaped — SessionStart/UserPromptSubmit
        // reuse the Claude handlers under agent='codex' (ctx set above).
        case 'codex-session-start': {
            const out = await handleSessionStart(ctx, input);
            return out ? JSON.stringify(out) : null;
        }
        case 'codex-user-prompt': {
            const out = await handleUserPrompt(ctx, input);
            return out ? JSON.stringify(out) : null;
        }
        case 'codex-post-tool': {
            const out = await handleCodexPostTool(ctx, input);
            return out ? JSON.stringify(out) : null;
        }
        case 'codex-stop': await handleCodexStop(ctx, input); return null;
        // Grok payloads are normalized above. SessionStart reuses the Claude
        // handler for the session boundary + project-cache refresh; its
        // briefing JSON is returned but Grok ignores passive-event stdout —
        // the briefing reaches Grok via store_read (AGENTS.md instructs it).
        case 'grok-session-start': {
            const out = await handleSessionStart(ctx, input);
            return out ? JSON.stringify(out) : null;
        }
        case 'grok-user-prompt': {
            const out = await handleUserPrompt(ctx, input);
            return out ? JSON.stringify(out) : null;
        }
        case 'grok-post-tool': await handleGrokPostTool(ctx, input); return null;
        case 'grok-stop': await handleGrokStop(ctx, input); return null;
        case 'grok-session-end': await handleSessionEnd(ctx, input); return null;
        case 'post-checkout': await handlePostCheckout(ctx, rest); return null;
        default: return null;
    }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main()
        .catch(() => null) // capture never blocks the session
        .then((out) => {
            // Exit via callback so piped stdout flushes on Windows.
            if (out) process.stdout.write(out, () => process.exit(0));
            else process.exit(0);
        });
}

# AGENTS.md — Taho Browser Project Guidelines

## Project Identity & Lead Orchestrator
This repository is governed by the **Taho Lead Orchestrator** persona. When working on this codebase:
- Always preserve the architectural boundaries documented in `Docs/TAHO_BROWSER_SYSTEM_ARCHITECTURE.md` and `Docs/TAHO_BROWSER_IMPLEMENTATION_PLAN.md`.
- Orchestrate tasks using the specialized Agency Agents installed in `~/.gemini/config/skills/` and `.agents/skills/`.

## Non-Negotiable Invariants
1. **Pure JVM Isolation**:
   - `:capture:domain`, `:transfer:core`, and `:contract:taho-transfer` are strictly pure JVM modules.
   - **Never** introduce `import android.*` into these modules.
2. **Session Attribution Join Protocol**:
   - GeckoView does not expose a `tabId` on `GeckoSession`.
   - Never attempt direct tab ID lookup; all network observations must be joined via the content-script handshake protocol.
3. **Capture Decoupling**:
   - Browsing performance and stability must remain flawless even if capture is toggled off or fails.
4. **Credential Isolation**:
   - Strictly adhere to credential redaction policies before transferring request data to Taho. Never write unredacted bearer tokens or passwords to disk or logs.

## Agent Delegation Matrix
- **GeckoView Runtime & Native Android**: Activate `agency-android-engineer`
- **Compose Shell & AMOLED UI**: Activate `agency-frontend-developer` & `agency-ui-designer`
- **Capture Domain & Persistence**: Activate `agency-backend-architect` & `agency-database-optimizer`
- **Transfer Contract & IPC**: Activate `agency-api-platform-engineer`
- **Security & Secret Scrubbing**: Activate `agency-application-security-engineer`
- **Testing & Verification**: Activate `agency-api-tester` & `agency-evidence-collector`

# TestPilot Core Flow Implementation Plan

> **For agentic workers:** Execute inline using the confirmed project SPEC. This plan tracks the work requested by the user.

**Goal:** Align the working application with the agreed AI recording → interface orchestration → AI case generation → save flow while preserving authentication and admin permissions.

**Architecture:** Retain Spring Boot/JPA/Spring AI/Playwright MCP and Vue 3. Replace the old confirmation-heavy task flow with explicit task draft, recording, scene, generation, and save operations. Keep identifiers out of variable URL path segments. Reuse shared domain models where possible and add only fields needed by the new flow.

**Tech Stack:** Java 17, Spring Boot 3, Spring AI, JPA, PostgreSQL, Playwright MCP, Vue 3, Vite.

**Spec:** `spec/SPEC-2026-10-19-API.md`, `spec/SPEC-2026-10-19.md`.

## Global Constraints

- Preserve registration, login, JWT and admin role checks.
- Resource identifiers are query/body parameters; no variable URL path segments in the new APIs.
- AI recording executes the user-provided browser goal and captures network calls during execution.
- Orchestration contains API ordering only; request data, preconditions and assertions belong to generated cases.
- Copying a task copies recording information only.
- Shared rules and cases are readable by all; only owners/admin can mutate.

## Review Focus

- Missing LLM credentials or browser MCP availability must produce actionable failures instead of silent mock success.
- Secrets in captured headers/bodies must be masked before persistence and model prompts.
- AI responses must be parsed and validated as structured case drafts before saving.
- Copy/update/delete paths must enforce owner/admin authorization.
- Existing legacy endpoints should not break auth/profile flows while routes migrate.

---

### Task 1: Task draft and AI recording contract

**Files:** `AgentTask`, task DTOs, `AgentTaskController`, `AgentTaskService`, browser provider and recording-agent configuration.

- [ ] Add task name, recording goal and filter prompt; creation saves a draft without starting work.
- [ ] Add fixed recording start/stop/status routes and execute browser actions from the recording goal while capturing network events.
- [ ] Mask sensitive request and response values before storing/calling an LLM.
- [ ] Remove the old implicit create-and-start behavior from the UI path.

### Task 2: Scene orchestration and generated cases

**Files:** task/rule/case DTOs, `AgentTaskController`, rule/case services, LLM prompt/service.

- [ ] Persist multiple named ordered API scenes from recordings and history.
- [ ] Generate structured case drafts per scene, with public parameters and per-step input/pre/post fields.
- [ ] Add an explicit save action that writes selected drafts to the shared case library.
- [ ] Align rule and case CRUD routes to fixed paths with IDs in query/body; retain run/environment support only if still linked.

### Task 3: Frontend workflow and libraries

**Files:** `frontend/src/api.js`, task wizard and task/rule/case views/components/styles.

- [ ] Match the agreed left navigation and three-node task flow.
- [ ] Support recording info, interface selection and multiple scenarios, AI generate then save.
- [ ] Show rule and case lists/details with owner-aware actions; case steps use input/pre/post tabs.
- [ ] Keep login/profile and admin behavior.

### Task 4: Safe cleanup and review

- [ ] Remove only legacy workflow code that has no remaining references after migration.
- [ ] Update README/spec implementation notes and run frontend build plus backend compile if dependencies are available.
- [ ] Summarize reusable, replaced, and intentionally retained code.

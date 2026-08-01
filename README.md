# Meeting Priority Agent

A Spring Boot + Spring AI agent that reads **today's Google Calendar events**, classifies
each meeting into a priority tier using fixed rules, and (optionally) asks an LLM (Claude,
via Spring AI's Anthropic starter) to turn that into a short daily briefing.

## Priority rules

| Tier | Rule | Priority |
|---|---|---|
| Small meeting | Exactly 2 people total (you + 1 other) | **Highest** |
| Group meeting | 3+ people, no "department" signal | Medium |
| Department meeting | Title/description matches a department keyword (e.g. "all-hands", "town hall", "team sync") | **Lowest** |

Headcount alone can't tell a regular group meeting apart from a recurring department
sync, so the department tier is detected by keyword matching against the event title and
description. Edit the keyword list in `application.yml` under `meeting-agent.department-keywords`
to match how your organization actually names these meetings (or swap in your own rule —
e.g. match on a specific calendar, organizer, or recurring-event ID — in
`MeetingPriorityService`).

## Project layout

```
src/main/java/com/example/meetingagent/
  MeetingAgentApplication.java        - Spring Boot entry point
  config/GoogleCalendarConfig.java    - OAuth2 + Calendar API client bean
  model/Meeting.java                  - Simplified event model
  model/MeetingPriority.java          - Priority tier enum
  service/GoogleCalendarService.java  - Pulls + maps today's events
  service/MeetingPriorityService.java - Deterministic classification + sorting
  service/MeetingAgentService.java    - The "agent": orchestrates + calls the LLM
  controller/MeetingController.java   - REST endpoints
src/main/resources/application.yml    - Config (API keys, calendar id, keywords)
```

## 1. Set up Google Calendar access

1. Go to the [Google Cloud Console](https://console.cloud.google.com/), create (or pick) a
   project.
2. **APIs & Services > Library** → enable the **Google Calendar API**.
3. **APIs & Services > Credentials** → **Create Credentials > OAuth client ID**.
   - Application type: **Desktop app**.
4. Download the resulting JSON and save it as:
   ```
   src/main/resources/credentials.json
   ```
   (This file contains a client ID/secret, not a user token — still, don't commit it to a
   public repo. Add `credentials.json` and `tokens/` to `.gitignore`.)
5. First time the app runs, it opens a browser window asking you to sign in and grant
   **read-only** calendar access. The resulting token is cached under `tokens/` so future
   runs don't prompt again. If you're running this on a headless server, do this first
   run on a machine with a browser, then copy the `tokens/` directory over.

## 2. Set up the LLM (Claude via Spring AI)

Export an Anthropic API key:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

`application.yml` is already wired to use it (`spring.ai.anthropic.api-key`). If you'd
rather use OpenAI, swap the `spring-ai-starter-model-anthropic` dependency in
`pom.xml` for `spring-ai-starter-model-openai` and update the `spring.ai.openai.*`
config accordingly — `MeetingAgentService` uses the generic `ChatClient`, so no other code
changes are needed.

## 3. Run it

```bash
mvn spring-boot:run
```

On first launch a browser tab opens for Google OAuth consent (see above). Once
authorized, the app serves on `http://localhost:8080`.

## 4. Endpoints

**Prioritized list only** (fast, no LLM call — good for driving a UI):
```bash
curl http://localhost:8080/api/meetings/today
```

**Full agent output** (prioritized list + natural-language briefing):
```bash
curl http://localhost:8080/api/meetings/today/briefing
```

Example response shape from `/briefing`:
```json
{
  "meetings": [
    { "title": "1:1 with Sam", "priority": "SMALL_MEETING", "...": "..." },
    { "title": "Roadmap review", "priority": "GROUP_MEETING", "...": "..." },
    { "title": "Eng Department Sync", "priority": "DEPARTMENT_MEETING", "...": "..." }
  ],
  "briefing": "• 9:00–9:30 1:1 with Sam — highest priority, no gap before your 10am...\n..."
}
```

## Versions used

- **Spring Boot 4.1.0** (Spring Framework 7) — required baseline for Spring AI 2.0.
- **Spring AI 2.0.0** (GA, June 2026) — this project's code only uses the plain `ChatClient.prompt().user(...).call().content()` path with no tool calling, chat memory, or MCP, so none of Spring AI 2.0's breaking changes affect it. The `spring.ai.anthropic.chat.options.model` property path is unchanged from 1.x.
- Java 17 (Spring Boot 4.1 supports Java 17 through 26; bump `java.version` in `pom.xml` if you want a newer LTS).

If you're upgrading an *existing* Spring AI 1.x project rather than starting from this one, check Spring's [upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) first — 2.0 removed several deprecated APIs (tool-calling internals, `PromptChatMemoryAdvisor`, Azure OpenAI module, etc.) that don't apply here but might affect a more feature-rich app.

## Notes / things to adapt for your setup

- **Time zone**: "today" is computed in the JVM's system time zone by default. Set
  `google.calendar.zone-id` in `application.yml` to pin it explicitly.
- **Which calendar**: defaults to `primary`. Set `google.calendar.calendar-id` to read a
  different calendar (e.g. a shared/department calendar), though note the OAuth user must
  have access to it.
- **Declined events**: events you've explicitly declined are filtered out automatically.
- **Rooms/resources**: room/resource calendar entries in the attendee list are excluded
  from headcount so a booked conference room doesn't inflate a 1:1 into a "group meeting."
- **3-person edge case**: the user's original rules only defined "2 people" (highest) and
  "more than 3" (group). A meeting with exactly 3 people and no department keyword match
  is currently bucketed as `GROUP_MEETING` — adjust `MeetingPriorityService.classify()` if
  you want different behavior for that edge case.

## A note on this build

This project was written but **not compiled or run** in this environment (no access to
Maven Central here to resolve dependencies). Review it like a first draft from a
colleague — run `mvn spring-boot:run` locally and skim the OAuth/Spring AI wiring before
you rely on it. The two integration points most likely to need small local fixes are:
- Spring AI's `ChatClient` auto-configuration bean naming (occasionally shifts between
  Spring AI milestone versions — bump `spring-ai.version` in `pom.xml` if the starter
  artifact name has changed since this was written).
- The exact Claude model string in `application.yml` (`spring.ai.anthropic.chat.options.model`) —
  check Anthropic's current model list and update if needed.

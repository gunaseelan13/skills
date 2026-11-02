# Skills

Agent skills for Claude Code, Cursor, Codex and other coding agents.

## empty-film-state

An empty state or feature explainer that shows instead of tells: a short looping skeleton film on
the top half of a phone (or a browser window), then a title, one sentence and the way in.

```bash
npx skills add gunaseelan13/skills --skill empty-film-state
```

### Ask for it

Name the screen and what the feature does. The agent writes the film's story, wires it to your
theme and builds the state with your own components.

- "Add an empty state to the Invoices page: show creating an invoice, with New invoice as the action."
- "The Team page is empty for new workspaces. Make an empty film state that shows inviting someone."
- "Explain the Offline mode setting with a film until it's turned on. The button opens its settings."

It works on:

| Platform | Engine | Example |
|---|---|---|
| Web, any framework | `assets/skeleton-film.js`, one canvas file, no dependencies | `webapp-demo.html`: a desktop app page; `demo.html`: mobile |
| Android, Views and Material 3 | `assets/android/` | `ProjectsFilmView.kt` |
| iOS, SwiftUI | `assets/ios/` | `ProjectsFilm.swift` |

To see the web examples, serve `skills/empty-film-state/assets` (`python3 -m http.server`) and
open `webapp-demo.html` or `demo.html`. Add `?theme=dark` for dark mode.

## License

MIT, see [LICENSE](LICENSE).

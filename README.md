# Skills

Agent skills for Claude Code, Cursor, Codex and other coding agents.

## empty-film-state

An empty state or feature explainer that shows instead of tells: a short looping skeleton film on
the top half of a phone (or a browser window), then a title, one sentence and the way in.

```bash
npx skills add gunaseelan13/skills --skill empty-film-state
```

Comes with a framework-free canvas engine (`assets/skeleton-film.js`) and a demo. To see it, serve
`skills/empty-film-state/assets` (`python3 -m http.server`) and open `demo.html`.

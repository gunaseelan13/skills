---
name: empty-film-state
description: Design and build an empty state or feature explainer as a short looping skeleton film on a half phone (or browser window), with a title, one-line description and actions. Use when a list or page has nothing in it yet, when a feature needs a "how it works" before it is turned on, or when asked for an "empty film state", "empty screen film", "explainer film" or "EmptyFilmState".
license: MIT
---

# Empty Film State

An empty screen that shows instead of tells: a few seconds of the feature at work, drawn as
skeleton UI on the top half of a device that fades into the page, then what it is and the way in.

```
┌───────────────────────────┐
│   ╭───────────────────╮   │  film: device frame, top half only,
│   │ ▬▬   ▬▬▬▬▬     ◯  │   │  skeleton screen, looping story
│   │ ╭───────────────╮ │   │
│   │ │ ◯ ▬▬▬▬▬     ⬭ │ │   │
│   │ ╰───────────────╯ │   │
│   ░░░░░░░░░░░░░░░░░░░░░   │  bottom fades into the page
│     Title in a few words  │  title
│  One sentence, what it    │  description: one short sentence
│        does for you.      │
│ [      Primary action    ]│  primary (optional)
│ [     Secondary action   ]│  secondary (optional, quieter)
└───────────────────────────┘
```

Build it on the web from `references/web.md`, with a working engine in `assets/skeleton-film.js`.
On another platform, port that engine and keep the rules.

The rules below hold on every platform. A state that breaks one is a bug, even when it looks fine.

## Words

- **Title**: what the feature gives you, in two to five words, sentence case. "Reuse prompts with a
  shortcut", "Gestures on Home". Not "No snippets yet".
- **Description**: one short sentence, about 60 to 80 characters, at most two lines on a phone.
  Say what it does, nothing more: "Save a prompt you send often, then type its shortcut in chat to
  fill it in." The film already shows how; a paragraph here buries the button.
- **Primary**: a verb phrase naming the outcome or the place it goes. "New Snippet",
  "Turn On in Accessibility". When a permission is the reason, it goes in this label, not in the
  description.
- **Secondary**: the quieter second way in ("Browse examples"). Leave it out unless there is one.
- **Caveats and warnings** (privacy, side effects) go in body text **below** the buttons, never in
  the description.
- A feature explainer that is already on shows no film at all; show its settings instead.

## The film

**Frame.** The top half of a device, the bottom fading into the page so the device has no bottom
edge. On mobile it is a phone: 288 px wide, 44 px corner radius, 8 px bezel, a status bar (28 px,
camera dot centred) and an optional 40 px toolbar band. The film slot is 380 px tall. Screen
content is laid out at 328 px wide, as on a real page, and scaled down to fit the screen. The web
uses a browser window or app frame instead when the feature lives on the desktop (see web.md).

**Skeletons, not pixels.** Everything on the screen is a bar, block or circle in a hint of the
text colour over the surface it sits on (about 7.5% ink by day, 10% by night), with a soft
highlight sweeping across all of them as one light (a 1.6 s pass every 2.6 s, leaning a little,
as if lit from the top left). Only real colour is the accent, for the thing the feature turns on
(a switch, a send button, the new item). Keep words out of the film, except for what the feature
itself puts on screen, such as a time or the `/` that calls it up.

**Clip to the glass.** Everything drawn on the screen, including shades, scrims, sheets and
full-screen overlays, is clipped to the screen's rounded outline. Nothing pokes past the frame's
corners. Clip to a rounded path, never a plain rectangle.

**Story.** One idea, three to six beats, 8 to 15 s a loop:

1. Where you start (often the empty or "before" state).
2. The action, shown by a touch on a phone (a dot where the finger lands, its ring spreading) or a
   mouse pointer on desktop (it glides to the control, presses in, and rings).
3. The result appearing.
4. Optionally, a "before and after" contrast, or a follow-on beat for a second gesture.
5. Everything fades out so the loop starts clean.

Feedback for a failed try is a small physical cue, not text: for example a quick side-to-side
shake of the screen (3 swings over 0.5 s, about 7 px, dying away).

A device state change is shown as the device would: locking or turning off is a fade to black over
about 0.7 s that holds for a second or two, not a cut.

**Timing.** Write each beat as a second within the loop, and stretch the whole script with one
pace factor (1.3 is common) rather than retiming beats. Use smoothstep easing for fades and moves;
linear only for spreads and progress. Cross-fades between screens overlap by 0.3 to 0.5 s.

**Playback.**

- Play 3 loops while visible, then rest on one chosen frame: the one that best explains the
  feature on its own (the result showing, not a blank screen).
- Pause while off screen or hidden. Resume where it was.
- With reduced motion (`prefers-reduced-motion: reduce`, or the platform's own setting), show only the
  rest frame and never animate. The shimmer stops too.
- The film is one image to assistive tech: give it a description of the whole story, in order
  ("A phone showing a swipe down on Home that does nothing, then…").

**Theme.** Draw from the platform's tokens, never raw colours: page and card surfaces, text
primary and secondary for the skeleton ink, outline for the frame, the accent. It must read in
both light and dark.

## Layout

Spacing names are the design system's spacing scale (xs < sm < md < lg < xl); use its tokens.

- The block starts xl below whatever is above it. The film slot spans the content width, 380 px tall.
- Title (title style, primary text) md under the film; description (subtitle style, secondary
  text) xs under the title. Both centred; the description has an extra lg of side padding.
- Primary button xl under the description: full width, the standard 48 px button height.
  Secondary md under the primary, as the quieter variant.
- Body text for caveats sits under the buttons at the page's normal text size, start-aligned.

## Checklist

Before calling it done, look at real captures in light and dark mode, not only the code:

- [ ] The description is one sentence, two lines at most on a phone.
- [ ] Nothing spills past the frame's rounded corners at any point in the loop. Crop the top
      corners at full size to check.
- [ ] The rest frame explains the feature on its own.
- [ ] Reduced motion shows the rest frame, still.
- [ ] Every colour comes from a token. The accent is the only colour.
- [ ] The film's accessible description tells the whole story.

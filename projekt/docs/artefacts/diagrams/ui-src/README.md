# A4 UI mockups (source)

Each `ui-*.html` is one screen of A4 (D1–D7, W1, W2). `theme.css` holds the shared design tokens and
mirrors the planned JavaFX stylesheet `app.css`; `web.css` is the customer web page style.
Render all screens to `../ui-*.png` (2x) with Playwright:

    node render.js "$PWD" "$PWD/.." [filter]

The old PlantUML Salt wireframes are in `../../backup-a4-2026-11-10-pre-redesign/diagrams/`.

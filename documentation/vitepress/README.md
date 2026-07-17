# Dialogue Branch Documentation Hub (VitePress)

This folder contains the source for the Dialogue Branch Documentation Hub, built with [VitePress](https://vitepress.dev/), as hosted at https://www.dialoguebranch.com/docs/.

As of 2026-07-17, this is the live docs site. It replaced a previous Antora/AsciiDoc-based site (removed from the repo after the cutover); the content here was ported 1:1 from that source and is the canonical copy going forward.

## Structure

* `docs/` — the VitePress site root (source directory).
  * `docs/.vitepress/config.mts` — site config: nav, sidebar, search, etc.
  * `docs/.vitepress/theme/` — a thin extension of the default theme, adding the Dialogue Branch brand colors and self-hosted "Roboto Slab" font (matching `apps/web/src/assets/css/theme.css` and `fonts.css`) so the docs site and the Web Client Test Application look like one product.
  * `docs/public/` — static assets (logo, fonts, images) served as-is.
  * `docs/language/`, `docs/web-services/`, `docs/core-java/`, `docs/tutorials/`, `docs/contribution/` — the documentation content, one folder per section (mirroring the old Antora modules).

## Developing

```bash
npm install
npm run dev       # dev server with hot-reload
npm run build     # production build, output in docs/.vitepress/dist/
npm run preview   # preview the production build locally
```

`npm run build` also fails on dead internal links, so it doubles as a link checker for this content.

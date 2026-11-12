<script setup lang="ts">
// Mirrors the navbar of www.dialoguebranch.com (its `navbar.html` and navbar styles), so switching
// between Home / Documentation / About / News & Updates feels like staying on one site rather than
// jumping into a separate app: a bar of dark foliage with a ragged row of leaves hanging under it,
// which casts a shadow. The docs are hosted under the website's /docs/,
// so its pages are linked site-relative: they then also work on a local or test copy of the website.
import { withBase } from 'vitepress'
</script>

<template>
  <header class="dlb-site-header">
    <nav
      class="dlb-navbar"
      :style="{ '--dlb-foliage': `url(${withBase('/images/site/foliage.svg')})` }"
    >
      <a class="dlb-navbar-brand" href="/" target="_self">
        <img :src="withBase('/images/site/dlb-square-light.png')" alt="DialogueBranch home">
      </a>
      <input type="checkbox" id="dlb-hamburger" class="dlb-navbar-toggle">
      <label for="dlb-hamburger" class="dlb-navbar-burger" aria-label="Menu"><span></span></label>
      <ul class="dlb-navbar-menu">
        <li><a class="dlb-navbar-link" href="/" target="_self">Home</a></li>
        <li><a class="dlb-navbar-link" :href="withBase('/')" aria-current="page">Documentation</a></li>
        <li><a class="dlb-navbar-link" href="/about/" target="_self">About</a></li>
        <li><a class="dlb-navbar-link" href="/news/" target="_self">News &amp; Updates</a></li>
      </ul>
    </nav>
  </header>
</template>

<style scoped>
/* Undo VitePress's base styles (line height, font smoothing, box sizing, text colour), which
   the website doesn't have, so the navbar renders exactly as it does there. */
.dlb-navbar,
.dlb-navbar * {
  line-height: normal;
  -webkit-font-smoothing: auto;
  -moz-osx-font-smoothing: auto;
  text-rendering: auto;
  font-synthesis: weight style small-caps;
}

.dlb-navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  height: 80px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  font-family: 'Roboto Slab', 'Avenir Next W02', system-ui, -apple-system, 'Segoe UI', Roboto;
  font-size: 16px;
  color: #f7f3ec;
  background: var(--dlb-foliage) 0 0 / 320px 320px repeat, #134a15;
  filter: drop-shadow(0 4px 6px rgba(0, 0, 0, 0.5));
}

/* A ragged row of leaves hanging under the bar: two rows of rounded bumps of different sizes,
   overlapping, so the edge doesn't repeat visibly. */
.dlb-navbar::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  top: 100%;
  height: 18px;
  margin-top: -1px;
  background: var(--dlb-foliage) 0 0 / 320px 320px repeat, #134a15;
  -webkit-mask-image:
    radial-gradient(18px 12px at 50% 0, #000 98%, transparent 100%),
    radial-gradient(27px 17px at 50% 0, #000 98%, transparent 100%);
  -webkit-mask-size: 44px 18px, 71px 18px;
  -webkit-mask-position: 0 0, 23px 0;
  -webkit-mask-repeat: repeat-x;
  mask-image:
    radial-gradient(18px 12px at 50% 0, #000 98%, transparent 100%),
    radial-gradient(27px 17px at 50% 0, #000 98%, transparent 100%);
  mask-size: 44px 18px, 71px 18px;
  mask-position: 0 0, 23px 0;
  mask-repeat: repeat-x;
}

.dlb-navbar-brand img {
  display: block;
  height: 60px;
  width: auto;
}

.dlb-navbar-menu {
  display: flex;
  gap: 4px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.dlb-navbar-link {
  display: block;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 1.1em;
  font-weight: 500;
  color: #f7f3ec;
  text-decoration: none;
}

.dlb-navbar-link:hover {
  background-color: rgba(247, 243, 236, 0.12);
  color: #f7f3ec;
}

.dlb-navbar-link:focus-visible {
  outline: 2px solid #e3c08f;
  outline-offset: 2px;
}

/* The page you're on */
.dlb-navbar-link[aria-current="page"] {
  text-decoration: underline;
  text-decoration-color: #e3c08f;
  text-decoration-thickness: 3px;
  text-underline-offset: 8px;
}

/* The menu toggle, only shown on narrow screens */
.dlb-navbar-toggle {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.dlb-navbar-burger {
  display: none;
}

@media screen and (max-width: 1000px) {
  .dlb-navbar-burger {
    display: block;
    position: relative;
    width: 44px;
    height: 44px;
    cursor: pointer;
    user-select: none;
  }

  /* Three bars, drawn */
  .dlb-navbar-burger span,
  .dlb-navbar-burger span::before,
  .dlb-navbar-burger span::after {
    position: absolute;
    left: 8px;
    width: 28px;
    height: 3px;
    border-radius: 2px;
    background-color: #f7f3ec;
  }

  .dlb-navbar-burger span {
    top: 20px;
  }

  .dlb-navbar-burger span::before,
  .dlb-navbar-burger span::after {
    content: "";
    left: 0;
  }

  .dlb-navbar-burger span::before {
    top: -8px;
  }

  .dlb-navbar-burger span::after {
    top: 8px;
  }

  .dlb-navbar-toggle:focus-visible + .dlb-navbar-burger {
    outline: 2px solid #e3c08f;
    outline-offset: 2px;
  }

  /* The menu drops down below the bar, in the same foliage */
  .dlb-navbar-menu {
    display: none;
    position: absolute;
    top: 80px;
    left: 0;
    right: 0;
    flex-direction: column;
    gap: 0;
    padding: 4px 0 12px;
    background: var(--dlb-foliage) 0 0 / 320px 320px repeat, #134a15;
    box-shadow: 0 8px 16px rgba(0, 0, 0, 0.25);
  }

  .dlb-navbar-toggle:checked ~ .dlb-navbar-menu {
    display: flex;
  }

  .dlb-navbar-link {
    padding: 16px 24px;
    border-radius: 0;
    text-align: right;
    border-top: 1px solid rgba(247, 243, 236, 0.15);
  }
}
</style>

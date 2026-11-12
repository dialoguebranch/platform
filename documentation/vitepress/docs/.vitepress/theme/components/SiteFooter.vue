<script setup lang="ts">
// Mirrors the footer of www.dialoguebranch.com (its `footer.html` and footer styles): the grass,
// then a green that darkens towards the bottom with a few loose grass tufts, three columns of
// links and a small legal line, so the documentation reads as part of the website rather than a
// separate site. The website's tree trunk and roots are left out: the docs pages have no tree for
// them to belong to. The docs are hosted under the website's /docs/, so its pages are linked
// site-relative. Keep the text and links in step with the website's footer.
import { withBase } from 'vitepress'

// Filled in when the docs are built, like the website's own `build.year`
const year = new Date().getFullYear()
</script>

<template>
  <footer
    class="dlb-site-footer"
    :style="{
      '--dlb-grass': `url(${withBase('/images/site/grass.svg')})`,
      '--dlb-tufts': `url(${withBase('/images/site/footer-tufts.svg')})`
    }"
  >
    <div class="dlb-footer-grass"></div>
    <div class="dlb-footer-columns">
      <div class="dlb-footer-col dlb-footer-brand">
        <a href="/" target="_self">
          <img
            :src="withBase('/images/site/dlb-long-light.png')"
            width="276"
            height="40"
            alt="DialogueBranch home"
          />
        </a>
        <p>
          Branching dialogue, from script to app. Open source under the
          <a href="/about/#license" target="_self">MIT License</a>.
        </p>
      </div>
      <nav class="dlb-footer-col" aria-label="Explore">
        <h2>Explore</h2>
        <ul>
          <li><a href="/docs/" target="_self">Documentation</a></li>
          <li><a href="https://github.com/dialoguebranch/platform">GitHub</a></li>
          <li><a href="/about/" target="_self">About</a></li>
          <li><a href="/news/" target="_self">News &amp; Updates</a></li>
        </ul>
      </nav>
      <div class="dlb-footer-col">
        <h2>Contact</h2>
        <ul>
          <li><a href="mailto:info@dialoguebranch.com">info@dialoguebranch.com</a></li>
          <li><a href="https://bsky.app/profile/dialoguebranch.com">Bluesky</a></li>
        </ul>
      </div>
    </div>
    <div class="dlb-footer-legal">
      <p>
        &copy; 2023&ndash;{{ year }} <a href="https://www.fruittreelabs.com/">Fruit Tree Labs</a>,
        developed in collaboration with <a href="https://connectedcare.nl/">ConnectedCare</a>.
        DialogueBranch&trade; is a trademark of Fruit Tree Labs Creative Technology, LDA.
        Open-source contributions are &copy; their original authors.
      </p>
      <a class="dlb-footer-privacy" href="/privacy/" target="_self">Privacy</a>
    </div>
  </footer>
</template>

<style scoped>
.dlb-site-footer {
  position: relative;
  /* Above the fixed sidebar, so the footer slides over it at the bottom of the page. */
  z-index: calc(var(--vp-z-index-sidebar) + 1);
  width: 100%;
  padding-bottom: 50px;
  /* The grass colour right under the grass, fading to a darker green further down, with a few
     loose tufts scattered over it (the grass strip itself covers the top 100px) */
  background:
    var(--dlb-tufts) 40px 60px / 760px 320px repeat,
    linear-gradient(to bottom, #247527 100px, #134a15 100%);
  color: #f7f3ec;
  font-family: 'Roboto Slab', 'Avenir Next W02', system-ui, -apple-system, 'Segoe UI', Roboto;
  font-size: 15px;
  line-height: 1.6;
}

.dlb-footer-grass {
  position: relative;
  width: 100%;
  height: 100px;
  background-color: var(--vp-c-bg);
  background-image: var(--dlb-grass);
  background-repeat: repeat-x;
  background-position: bottom;
  background-size: 350px 50px;
  /* Overlap the green below by a pixel, so no hairline shows where the two meet */
  box-shadow: 0 1px 0 #247527;
}

/* Three columns, then one small line of legal text */
.dlb-footer-columns {
  display: grid;
  grid-template-columns: 1.4fr 1fr 1fr;
  gap: 32px;
  max-width: 960px;
  margin: 60px auto 0;
  padding: 0 5%;
}

.dlb-footer-brand img {
  display: block;
  width: 249px;
  max-width: 100%;
  height: auto;
}

.dlb-footer-brand p {
  max-width: 22em;
  margin: 14px 0 0;
  color: rgba(247, 243, 236, 0.85);
}

.dlb-footer-col h2 {
  margin: 0 0 10px;
  padding: 0;
  border: 0;
  font-family: inherit;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.6;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #e3c08f;
}

.dlb-footer-col ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.dlb-footer-col li + li {
  margin-top: 4px;
}

.dlb-site-footer a {
  color: #f7f3ec;
  font-weight: 600;
  text-decoration: none;
}

.dlb-site-footer a:hover {
  color: #f0b53f;
  text-decoration: underline;
}

.dlb-footer-brand p a,
.dlb-footer-legal p a {
  text-decoration: underline;
}

.dlb-footer-legal {
  display: flex;
  align-items: baseline;
  gap: 32px;
  max-width: 960px;
  margin: 40px auto 0;
  padding: 18px 5% 0;
  border-top: 1px solid rgba(247, 243, 236, 0.25);
  box-sizing: border-box;
  font-size: 12px;
  color: rgba(247, 243, 236, 0.75);
}

.dlb-footer-legal p {
  flex: 1;
  margin: 0;
}

/* The privacy page, on its own at the end of the bottom bar */
.dlb-site-footer a.dlb-footer-privacy {
  flex: none;
  font-weight: 700;
  white-space: nowrap;
}

@media screen and (max-width: 1000px) {
  .dlb-footer-columns {
    grid-template-columns: 1fr;
    gap: 22px;
    text-align: center;
  }

  .dlb-footer-brand img,
  .dlb-footer-brand p {
    margin-left: auto;
    margin-right: auto;
  }

  .dlb-footer-legal {
    flex-direction: column;
    align-items: center;
    gap: 12px;
    text-align: center;
  }
}
</style>

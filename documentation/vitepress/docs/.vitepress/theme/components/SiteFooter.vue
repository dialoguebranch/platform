<script setup lang="ts">
// Mirrors the footer of www.dialoguebranch.com (its `footer.html`: the grass, the tree trunk and
// the copyright line), so the documentation reads as part of the website rather than a separate
// site. Keep the text in step with the website's footer.
import { withBase } from 'vitepress'
</script>

<template>
  <footer
    class="dlb-site-footer"
    :style="{
      '--dlb-grass': `url(${withBase('/images/site/grass.svg')})`,
      '--dlb-trunk': `url(${withBase('/images/site/trunk-clear.svg')})`
    }"
  >
    <div class="dlb-footer-grass"></div>
    <div class="dlb-footer-trunk"></div>
    <p class="dlb-copyright">
      Copyright &copy; 2025, <a href="https://www.fruittreelabs.com/">Fruit Tree Labs</a>.
      DialogueBranch is a Trademark of Fruit Tree Labs Creative Technology, LDA. Open-Source
      contributions are &copy; of the original authors. Follow us on BlueSky
      (<a href="https://bsky.app/profile/dialoguebranch.bsky.social">@dialoguebranch.bsky.social</a>)
      for news and updates, or drop us an email at
      <a href="mailto:info@dialoguebranch.com">info@dialoguebranch.com</a> if you have any questions.
    </p>
  </footer>
</template>

<style scoped>
.dlb-site-footer {
  position: relative;
  /* Above the fixed sidebar, so the footer slides over it at the bottom of the page. */
  z-index: calc(var(--vp-z-index-sidebar) + 1);
  width: 100%;
  padding-bottom: 50px;
  background-color: #247527;
  color: #dddddd;
  font-family: 'Roboto Slab', 'Avenir Next W02', system-ui, -apple-system, 'Segoe UI', Roboto;
  font-size: 12px;
  font-weight: 400;
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
}

.dlb-footer-trunk {
  position: absolute;
  top: 0;
  left: 50%;
  width: 500px;
  height: 200px;
  margin-left: -250px;
  background-image: var(--dlb-trunk);
  background-repeat: no-repeat;
  background-position: center;
}

.dlb-copyright {
  position: relative;
  width: 70%;
  margin: 80px auto 0;
  text-align: center;
  font-size: 1em;
  font-weight: 900;
  line-height: 1.4;
}

.dlb-copyright a {
  color: #d1941a;
  font-weight: 600;
  text-decoration: none;
}

.dlb-copyright a:hover {
  color: #f0b53f;
}

@media screen and (max-width: 1000px) {
  .dlb-footer-trunk {
    left: 0;
    margin-left: -270px;
  }
}
</style>

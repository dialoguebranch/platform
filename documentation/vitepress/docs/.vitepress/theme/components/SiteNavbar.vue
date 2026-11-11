<script setup lang="ts">
// Mirrors the navbar of www.dialoguebranch.com, so switching between Home / Documentation /
// About / News & Updates feels like staying on one site rather than jumping into a separate app.
// The docs are hosted under the website's /docs/, so its pages are linked site-relative: they
// then also work on a local or test copy of the website.
import { withBase } from 'vitepress'
</script>

<template>
  <header class="dlb-site-header">
    <div class="dlb-navbar">
      <a href="/" target="_self">
        <img class="dlb-navbar-logo" :src="withBase('/dlb-square.png')" alt="Dialogue Branch">
      </a>
      <label for="dlb-hamburger">☰</label>
      <input type="checkbox" id="dlb-hamburger">
      <!-- The spaces between the items are deliberate: on the website, the line breaks between its
           inline-block <li>s render as small gaps, which Vue's compiler would otherwise strip. -->
      <ul>
        <li><a class="dlb-navbar-link" href="/" target="_self">Home</a></li>
        {{ ' ' }}
        <li><a class="dlb-navbar-link" :href="withBase('/')" aria-current="page">Documentation</a></li>
        {{ ' ' }}
        <li><a class="dlb-navbar-link" href="/about/" target="_self">About</a></li>
        {{ ' ' }}
        <li><a class="dlb-navbar-link" href="/news/" target="_self">News &amp; Updates</a></li>
      </ul>
    </div>
  </header>
</template>

<style scoped>
/* Undo VitePress's base styles (line height, font smoothing, box sizing, text colour), which
   the website doesn't have, so the navbar renders exactly as it does there. */
.dlb-navbar,
.dlb-navbar * {
  box-sizing: content-box;
  line-height: normal;
  -webkit-font-smoothing: auto;
  -moz-osx-font-smoothing: auto;
  text-rendering: auto;
  font-synthesis: weight style small-caps;
}

.dlb-navbar {
  font-family: 'Roboto Slab', 'Avenir Next W02', system-ui, -apple-system, 'Segoe UI', Roboto;
  font-size: 16px;
  color: #212121;
  background-color: #fafafa;
  position: fixed;
  left: 0;
  top: 0;
  width: 100%;
  z-index: 100;
  height: 80px;
  box-shadow: 0 4px 8px 0 rgba(0, 0, 0, .2);
}

.dlb-navbar-logo {
  float: left;
  width: auto;
  height: 60px;
  margin-top: 10px;
  margin-bottom: 10px;
  margin-left: 20px;
}

.dlb-navbar-link {
  float: right;
  display: block;
  font-size: 1.2em;
  color: #000;
  background-color: #fafafa;
  text-align: center;
  padding: 27px 16px;
  text-decoration: none;
  font-weight: 500;
}

.dlb-navbar-link:hover {
  text-decoration: none !important;
  background: #f0f0f0;
  color: #1e1e1e;
}

#dlb-hamburger,
.dlb-navbar label {
  display: none;
  user-select: none;
  float: right;
}

.dlb-navbar ul {
  float: right;
  list-style-type: none;
  margin: 0;
  padding: 0;
}

.dlb-navbar ul li {
  display: inline-block;
  box-sizing: border-box;
  margin: 0;
}

@media screen and (max-width: 1000px) {
  .dlb-navbar label {
    display: inline-block !important;
    color: #000;
    font-style: normal;
    font-size: 50px;
    padding: 2px 10px 2px 2px;
  }

  .dlb-navbar label:hover {
    color: #1e1e1e;
  }

  .dlb-navbar ul li,
  .dlb-navbar ul li a {
    display: block !important;
    float: none;
    text-align: right;
    background-color: #fafafa;
  }

  .dlb-navbar ul li {
    border-top: 1px solid #333;
  }

  .dlb-navbar ul:last-child {
    border-bottom: 1px solid #333;
    box-shadow: 0 4px 8px 0 rgba(0, 0, 0, .2);
  }

  .dlb-navbar ul {
    padding-top: 80px;
    display: none;
  }

  .dlb-navbar input:checked ~ ul {
    display: block !important;
    float: none;
  }
}
</style>

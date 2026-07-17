<script setup lang="ts">
// Mirrors the global `.dlb-navbar` embedded across www.dialoguebranch.com
// (including the current Antora-based /docs/ site this project replaces),
// so switching between Home / Documentation / About / News & Updates feels
// like staying on one site rather than jumping into a separate app.
</script>

<template>
  <header class="dlb-site-header">
    <div class="dlb-navbar">
      <a href="https://www.dialoguebranch.com/">
        <img class="dlb-navbar-logo" src="https://www.dialoguebranch.com/img/dlb-square.png" alt="Dialogue Branch">
      </a>
      <label for="dlb-hamburger">☰</label>
      <input type="checkbox" id="dlb-hamburger">
      <ul>
        <li><a class="dlb-navbar-link" href="https://www.dialoguebranch.com/">Home</a></li>
        <li><a class="dlb-navbar-link" href="https://www.dialoguebranch.com/docs/" aria-current="page">Documentation</a></li>
        <li><a class="dlb-navbar-link" href="https://www.dialoguebranch.com/about/">About</a></li>
        <li><a class="dlb-navbar-link" href="https://www.dialoguebranch.com/news/">News &amp; Updates</a></li>
      </ul>
    </div>
  </header>
</template>

<style scoped>
.dlb-navbar {
  font-family: 'Roboto Slab', 'Avenir Next W02', system-ui, -apple-system, 'Segoe UI', Roboto;
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
  font-size: 1.1em;
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

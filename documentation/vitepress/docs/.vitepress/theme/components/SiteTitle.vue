<script setup lang="ts">
// Replaces the default nav-bar site-title text (disabled via `siteTitle: false`
// in config.mts): the full title only fits without overlapping the search box
// on the home page's wider hero layout, so every other page gets the shorter form.
import { useData } from 'vitepress'

const { frontmatter } = useData()
</script>

<template>
  <span v-if="frontmatter.layout === 'home'">Dialogue Branch Documentation Hub</span>
  <span v-else>Documentation Hub</span>
</template>

import { h } from 'vue'
import DefaultTheme from 'vitepress/theme'
import type { Theme } from 'vitepress'
import SiteNavbar from './components/SiteNavbar.vue'
import SiteTitle from './components/SiteTitle.vue'
import './custom.css'

export default {
  extends: DefaultTheme,
  Layout() {
    return h(DefaultTheme.Layout, null, {
      'layout-top': () => h(SiteNavbar),
      'nav-bar-title-after': () => h(SiteTitle)
    })
  }
} satisfies Theme

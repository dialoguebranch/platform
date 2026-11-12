// Counts visits to the documentation with the same Matomo as www.dialoguebranch.com (site 3), so
// the docs show up in the website's statistics. That Matomo runs without cookies; the website's
// /privacy/ page explains what it records and offers the opt-out. Only on the real website: a
// local build, a test copy or someone else's copy of these docs sends nothing.
import { nextTick } from 'vue'
import type { Router } from 'vitepress'

const HOSTS = ['dialoguebranch.com', 'www.dialoguebranch.com']
const TRACKER = 'https://analytics.dialoguebranch.com/'
const SITE_ID = '3'

declare global {
  interface Window {
    _paq?: unknown[][]
  }
}

export function setUpAnalytics(router: Router): void {
  if (typeof window === 'undefined' || !HOSTS.includes(window.location.hostname)) return

  const paq = (window._paq = window._paq || [])
  paq.push(['setTrackerUrl', TRACKER + 'matomo.php'])
  paq.push(['setSiteId', SITE_ID])
  paq.push(['enableLinkTracking'])
  const script = document.createElement('script')
  script.async = true
  script.src = TRACKER + 'matomo.js'
  document.head.appendChild(script)

  // The docs change pages without reloading, so count each page the router shows, the first one
  // included. The previous page is passed on as the referrer, as a reload would have done.
  let previous: string | null = null
  const earlier = router.onAfterRouteChange ?? router.onAfterRouteChanged
  router.onAfterRouteChange = async (to) => {
    await earlier?.(to)
    await nextTick() // let the page's title update first
    if (previous) paq.push(['setReferrerUrl', previous])
    paq.push(['setCustomUrl', window.location.href])
    paq.push(['setDocumentTitle', document.title])
    paq.push(['trackPageView'])
    previous = window.location.href
  }
}

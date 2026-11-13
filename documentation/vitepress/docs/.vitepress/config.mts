import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { defineConfig } from 'vitepress'
import { withMermaid } from 'vitepress-plugin-mermaid'

// The docs are published under the project's website.
const SITE = 'https://www.dialoguebranch.com'

/**
 * The address a page is published at, for its canonical link: index.md → /docs/,
 * language/index.md → /docs/language/, compatibility.md → /docs/compatibility.html
 * (plain .html URLs, see `cleanUrls` below).
 */
function pageAddress(relativePath: string): string {
  if (relativePath === 'index.md') return '/docs/'
  if (relativePath.endsWith('/index.md')) return `/docs/${relativePath.slice(0, -'index.md'.length)}`
  return `/docs/${relativePath.replace(/\.md$/, '.html')}`
}

/**
 * A description for a page that doesn't set its own: the page's first paragraph as plain text,
 * cut at a word boundary to about what a search result shows. Headings, code, containers, HTML,
 * tables, lists, quotes and images are skipped. Straight double quotes become typographic ones:
 * VitePress writes the plain description tag without escaping them, so a straight one would end it early.
 */
function describe(markdown: string): string | undefined {
  const body = markdown.replace(/^---\n[\s\S]*?\n---\n/, '')
  for (const paragraph of body.split(/\n\s*\n/)) {
    const text = paragraph.trim()
    if (!text || /^(#|```|:::|<|\||[-*+]\s|\d+\.\s|>|!\[)/.test(text)) continue
    const plain = text
      .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
      .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
      .replace(/[`*_]/g, '')
      .replace(/\s+/g, ' ')
      .replace(/"([^"]*)"/g, '“$1”')
      .replace(/"/g, '”')
      .trim()
    if (plain.length < 40) continue
    if (plain.length <= 155) return plain
    return plain.slice(0, 155).replace(/\s+\S*$/, '') + '…'
  }
  return undefined
}

export default withMermaid(defineConfig({
  title: 'Dialogue Branch Documentation Hub',
  description: 'Documentation for the Dialogue Branch Platform: the language, the core Java library, the web services, and tutorials for getting started.',
  base: '/docs/',
  // "<page title> | Dialogue Branch Docs": short enough that a search result doesn't cut the page title
  titleTemplate: ':title | Dialogue Branch Docs',
  // Plain `.html` URLs: the docs are hosted under www.dialoguebranch.com/docs/ on a static host
  // that doesn't resolve extension-less ("clean") URLs, so those would 404 when opened directly.
  cleanUrls: false,
  lastUpdated: true,

  head: [
    ['link', { rel: 'icon', type: 'image/png', href: '/docs/dlb-square.png' }]
  ],

  // Every page gets its own description (its first paragraph) unless it sets one in its front matter;
  // otherwise search results would show the site-wide description under every page. The home page
  // keeps the site-wide one.
  transformPageData(pageData, { siteConfig }) {
    if (pageData.relativePath === 'index.md' || pageData.frontmatter.description) return
    const description = describe(readFileSync(join(siteConfig.srcDir, pageData.relativePath), 'utf8'))
    if (description) pageData.description = description
  },

  // A canonical link and the tags for link previews (social media, chat apps), as on the rest of the site.
  transformHead({ pageData, title, description }) {
    if (pageData.relativePath === '404.md') return
    const url = SITE + pageAddress(pageData.relativePath)
    return [
      ['link', { rel: 'canonical', href: url }],
      ['meta', { property: 'og:type', content: 'website' }],
      ['meta', { property: 'og:site_name', content: 'Dialogue Branch' }],
      ['meta', { property: 'og:title', content: title }],
      ['meta', { property: 'og:description', content: description }],
      ['meta', { property: 'og:url', content: url }],
      ['meta', { property: 'og:image', content: `${SITE}/img/social-preview.png` }],
      ['meta', { name: 'twitter:card', content: 'summary_large_image' }]
    ]
  },

  themeConfig: {
    logo: { src: '/dlb-square.png', alt: 'Dialogue Branch' },
    // Hides the default site-title text next to the logo — a custom SiteTitle
    // component is injected instead (see theme/index.ts), showing the full
    // "Dialogue Branch Documentation Hub" only on the home page and a shorter
    // "Documentation Hub" everywhere else, where the full title otherwise
    // overlapped the search box.
    siteTitle: false,

    nav: [
      { text: 'Language', link: '/language/' },
      { text: 'Web Services', link: '/web-services/' },
      { text: 'Core Java', link: '/core-java/' },
      { text: 'Tutorials', link: '/tutorials/' },
      { text: 'Compatibility', link: '/compatibility' },
      { text: 'Release Notes', link: '/release-notes' }
    ],

    sidebar: {
      '/language/': [
        {
          text: 'Language Definition',
          items: [
            { text: 'Overview', link: '/language/' },
            { text: 'Dialogue Branch Projects', link: '/language/dlb-project' }
          ]
        }
      ],
      '/web-services/': [
        {
          text: 'Web Services',
          items: [
            { text: 'Overview', link: '/web-services/' },
            { text: 'Authentication', link: '/web-services/authentication' },
            { text: 'API Service', link: '/web-services/api-service' },
            { text: 'BFF Service', link: '/web-services/bff-service' },
            { text: 'Studio', link: '/web-services/studio' },
            { text: '3rd Party Clients', link: '/web-services/third-party-clients' },
            { text: 'External Variable Service', link: '/web-services/external-variable-service' }
          ]
        }
      ],
      '/core-java/': [
        {
          text: 'Core Java Library',
          items: [
            { text: 'Overview', link: '/core-java/' }
          ]
        }
      ],
      '/tutorials/': [
        {
          text: 'Tutorials',
          items: [
            { text: 'Overview', link: '/tutorials/' },
            { text: 'Web Service - Installation', link: '/tutorials/webservice-installation' },
            { text: 'Web Service - Exploring the API', link: '/tutorials/webservice-exploringapi' }
          ]
        }
      ]
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/dialoguebranch/platform' }
    ],

    search: {
      provider: 'local'
    },

    editLink: {
      pattern: 'https://github.com/dialoguebranch/platform/edit/main/documentation/vitepress/docs/:path',
      text: 'Edit this page on GitHub'
    }
  }
}))

import { defineConfig } from 'vitepress'
import { withMermaid } from 'vitepress-plugin-mermaid'

export default withMermaid(defineConfig({
  title: 'Dialogue Branch Documentation Hub',
  description: 'Documentation for the Dialogue Branch Platform: the language, the core Java library, the web services, and tutorials for getting started.',
  base: '/docs/',
  cleanUrls: true,
  lastUpdated: true,

  head: [
    ['link', { rel: 'icon', type: 'image/png', href: '/docs/dlb-square.png' }]
  ],

  themeConfig: {
    logo: '/dlb-square.png',
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
    },

    footer: {
      message: 'Released under the MIT License.',
      copyright: 'Copyright © Fruit Tree Labs'
    }
  }
}))

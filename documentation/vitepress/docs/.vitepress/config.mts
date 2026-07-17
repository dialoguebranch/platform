import { defineConfig } from 'vitepress'

export default defineConfig({
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

    nav: [
      { text: 'Language', link: '/language/' },
      { text: 'Web Services', link: '/web-services/' },
      { text: 'Core Java', link: '/core-java/' },
      { text: 'Tutorials', link: '/tutorials/' },
      { text: 'Contributing', link: '/contribution/' },
      { text: 'dialoguebranch.com', link: 'https://www.dialoguebranch.com' }
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
            { text: 'Release Notes', link: '/web-services/release-notes' }
          ]
        }
      ],
      '/core-java/': [
        {
          text: 'Core Java Library',
          items: [
            { text: 'Overview', link: '/core-java/' },
            { text: 'Release Notes', link: '/core-java/release-notes' }
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
      ],
      '/contribution/': [
        {
          text: 'Contributing',
          items: [
            { text: 'Overview', link: '/contribution/' }
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
})

// See https://docusaurus.io/docs/site-config.html for all the possible
// site configuration options.

const repoUrl = "https://github.com/TimWSpence/cats-stm";
const gitterUrl = "https://gitter.im/cats-stm/community";
const baseUrl = "/cats-stm/";
const apiUrl = `${baseUrl}api/index.html`;

const siteConfig = {
  title: "Cats STM",
  tagline: "Software transactional memory for Cats Effect",
  url: "https://timwspence.github.io/cats-stm/",
  baseUrl: baseUrl,
  apiUrl: apiUrl,

  // Used for publishing and more
  projectName: "cats-stm",
  organizationName: "TimWSpence",

  // For no header links in the top nav bar -> headerLinks: [],
  headerLinks: [
    { doc: "theory/intro", label: "Docs" },
    { href: apiUrl, label: 'API'},
    { href: repoUrl, label: "GitHub", class: "github", external: true }
  ],

  /* path to images for header/footer */
  headerIcon: "img/logo.png",

  /* Colors for website */
  colors: {
    primaryColor: '#d36d6f',
    secondaryColor: '#294066',
  },

  customDocsPath: "cats-stm-docs/target/mdoc",

  stylesheets: [baseUrl + "css/custom.css"],

  // This copyright info is used in /core/Footer.js and blog rss/atom feeds.
  copyright: `Copyright © 2018-${new Date().getFullYear()} Tim Spence`,

  highlight: {
    // Highlight.js theme to use for syntax highlighting in code blocks.
    theme: 'github-gist',
    defaultLang: 'plaintext'
  },

  /* On page navigation for the current documentation page */
  // onPageNav: "separate",

  separateCss: ["api"],

  twitterUsername: 'TimWSpence',
  twitterImage: 'img/undraw_tweetstorm.svg',

  scripts: ['https://buttons.github.io/buttons.js'],

  repoUrl,
  gitterUrl
};

module.exports = siteConfig;

---
layout: home

hero:
  name: Dialogue Branch
  text: Documentation Hub
  tagline: Author, execute, and serve branching dialogue scripts. Documentation for the language, the core Java library, the web services, and tutorials for getting started.
  image:
    src: /dlb-logo-medium-bright.png
    alt: Dialogue Branch
  actions:
    - theme: brand
      text: Language Definition
      link: /language/
    - theme: alt
      text: Web Services
      link: /web-services/
    - theme: alt
      text: Tutorials
      link: /tutorials/

features:
  - title: Dialogue Branch Language
    details: The .dlb script format — nodes, statements, variables, conditionals and replies — that all Dialogue Branch content is written in.
    link: /language/
    linkText: Read the language definition
  - title: Core Java Library
    details: The library that parses and executes .dlb scripts, published to Maven Central as com.dialoguebranch:dlb-core-java.
    link: /core-java/
    linkText: Explore the core library
  - title: Web Services
    details: A Spring Boot REST API that wraps the core library, plus Dialogue Branch Studio, a Vue-based app for trying it out and authoring dialogues.
    link: /web-services/
    linkText: Read the web services docs
  - title: Tutorials
    details: Step-by-step guides for deploying the Web Service locally and exploring its API.
    link: /tutorials/
    linkText: Start a tutorial
---

<div class="vp-doc" style="max-width: 720px; margin: 0 auto; padding: 0 24px 64px;">

The Dialogue Branch Platform ([`dialoguebranch/platform`](https://github.com/dialoguebranch/platform) on GitHub) is a monorepo consisting of three components that work together: the [Core Java Library](/core-java/) for parsing and executing `.dlb` scripts, the [Web Service](/web-services/) that wraps the core library in a REST API (and includes Dialogue Branch Studio, a Vue-based app for trying it out and authoring dialogues), and the [Dialogue Branch Language](/language/) that the `.dlb` scripts themselves are written in.

> Did you find an error? Or you didn't find what you were looking for? Please send your feedback to [info@dialoguebranch.com](mailto:info@dialoguebranch.com).

</div>

# Dialogue Branch Projects

A single .dlb script might be all you need for your Dialogue Branch Powered application. But once the content offered by your virtual agents is expanding, it becomes important to split your work into several scripts. Additionally, you might want to make your dialogue content available to users that speak different languages, so you will need "translation files" (.json files) to accompany your dialogues.

Defining a "Dialogue Branch Project" is the way to organize your various Dialogue Branch scripts and various translation files into a single manageable collection.

## Basic Dialogue Branch Project

The most basic version of a Dialogue Branch project is a (root) folder that contains a `dlb-project.xml` metadata file, alongside one sub-folder per supported language. Each language folder contains the `.dlb` scripts (for the source language) or `.json` translation files (for translation languages) for that language; arbitrary further sub-folder structures may be used within a language folder to organize your set of `.dlb` and `.json` files — any `.dlb`/`.json` file found anywhere below a language folder is picked up automatically.

```text
my-project/
  dlb-project.xml
  en/                 <- source language folder (code "en" matches <source-language code="en"/>)
    basic.dlb
    menu.dlb
    quests/
      dragon-quest.dlb
  nl-NL/               <- translation language folder, holds .json translations, not .dlb
    basic.json
    menu.json
    quests/
      dragon-quest.json
```

## Concepts in a Dialogue Branch Project

Without going into the question of how to store this information, we first describe all the conceptual parts of a Dialogue Branch Project.

### Basic Metadata

A Dialogue Branch Project should define the following basic metadata elements:

* Name — A Dialogue Branch Project has a name, which can be any string. This is the only required piece of metadata.
* Version — A Dialogue Branch Project has a free-form version indicator (e.g. `v0.1.0`), which can be any string. Optional, defaults to an empty string.
* Description — A Dialogue Branch Project has a description, which can be any string of any length.

::: info Note
When a project is stored in the Web Service's database rather than as files, its name is limited to 255 characters, to ensure compatibility between Dialogue Branch Projects that are stored as files vs Dialogue Branch Projects stored in a relational database. This limit does not apply to the version or description, nor to any of these fields when working with project files directly.
:::

::: info Note
When a project is authored through the web client's visual editor (see [Dialogue Branch Web Services](/web-services/)), it is additionally identified by a `projectSlug` — a short, URL-safe identifier used throughout the Web Service API to select which project a call applies to. This is the same identifier as the `slug` attribute described below; the Web Service assigns it when the project is created, and writes it back into the exported `dlb-project.xml` file's root element.
:::

### Language Map Metadata

A Dialogue Branch Project should define the set of languages for which content is available by declaring a **Language Map**.

A **Language Map** consists of exactly one **Source Language**, and 0 or more **Translation Languages**.

Both a **Source Language** and a **Translation Language** are defined by a *name* and a *code*.

![Dialogue Branch project language map](/images/dlb-project-language-map.png)

## Dialogue Branch Project Metadata file

A Dialogue Branch Project's metadata is stored in a `dlb-project.xml` file at the root of the project. The root element is `dlb-project`, with a required `name` attribute and optional `version` and `slug` attributes. `slug` (shown below) is normally only populated by the Web Service, when it writes out a project's metadata as part of its Export Project feature — see the Note on `projectSlug` above; when hand-authoring a project file yourself, you can safely omit it:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<dlb-project name="My Dialogue Branch Project" version="1" slug="my-dialogue-branch-project">
  <description>A short description of this project.

  Descriptions may span multiple lines.</description>
  <language-map>
    <source-language name="English" code="en" />
    <translation-language name="Dutch (Netherlands)" code="nl-NL" />
    <translation-language name="Portuguese (Portugal)" code="pt-PT" />
  </language-map>
</dlb-project>
```

The `code` attribute of each `<source-language>`/`<translation-language>` element must match the name of its corresponding language folder at the project root (e.g. `code="en"` corresponds to the `en/` folder shown above).

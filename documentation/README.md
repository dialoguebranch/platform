# Dialogue Branch Documentation Hub
This repository contains the source files for the Dialogue Branch Documentation Hub in ASCIIDOC format (www.asciidoc.org) that can be compiled into the documentation website as hosted at https://www.dialoguebranch.com/docs/ using Antora.

## Documentation Content
The actual documentation content can be found in the `/docs/` folder, where the different documentation modules can be found.

## DrawIO Source Files
In the `/drawio/` folder, source files (.drawio) for diagrams used in the documentation can be found.

## Antora Playbooks
This repository also contains an "Antora Playbook" file for generating the Dialogue Branch Documentation Hub website, as well as the UI Bundle (`./dlb-ui-bundle-latest.zip`), which is a modification of the Antora Default UI.

See https://docs.antora.org/antora/2.3/ for detailed documentation on using Antora.

In short, if you want to generate the Dialogue Branch Documentation Hub:

 - Install Antora Command Line Interface (CLI): https://docs.antora.org/antora/2.3/install/install-antora/
 - Clone the `platform` monorepo (this documentation now lives at `platform/documentation/`)
 - Open a terminal in `{$GIT_FOLDER}/platform/documentation/`
 - Run `antora --fetch antora-playbook.yml`
 - This will generate the `/build/` folder by compiling the local sources
 - You can find the output in `/build/site/index.html` which is the entry-point for the documentation

## Antora "Dialogue Branch" UI
The `/dlb-ui/` folder contains the source for generating the Dialogue Branch UI Bundle, which is a fork of the Antora Default UI (https://gitlab.com/antora/antora-ui-default). For detailed instructions on how to make modifications to the UI and build the ui-bundle.zip file that is used in the Antora Playbook, see: https://docs.antora.org/antora-ui-default/

As a quick reference on building the `ui-bundle.zip`. Do the following once:
- Install Gulp (e.g. `apt install gulp`).
- Run `npm install` inside the `/dlb-ui/` folder.

Then:
 * Use `gulp preview` to launch a preview server (at `http://localhost:5252`), to preview the UI bundle and quickly test changes.
 * Use `gulp bundle` to create the ui package (generates a new version of `/dlb-ui/build/ui-bundle.zip` (copy this to `./dlb-ui-bundle-latest.zip` once everything looks fine and you want to use it to generate a new documentation site using the Antora Playbook mentioned above).

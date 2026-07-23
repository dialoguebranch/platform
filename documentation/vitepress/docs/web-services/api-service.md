# Web Services: API

The Dialogue Branch Web Service source code is part of this monorepo, in the [`apps/api`](https://github.com/dialoguebranch/platform/tree/main/apps/api) folder. It is a pure [OAuth2](https://oauth.net/2/) resource server — user authentication itself is handled entirely by Keycloak, see [Authentication](/web-services/authentication) — that wraps the Dialogue Branch Core library with a REST API.

The Gradle wrapper (`./gradlew`) in `apps/api` is used to build the service, which is packaged as a WAR file for deployment on [Tomcat 10](https://tomcat.apache.org/download-10.cgi), or built as a Docker image using the provided `Dockerfile` (built from the repository root, since the Docker build context spans both `apps/api/` and `packages/core/`). A detailed installation tutorial is provided here: [Dialogue Branch Web Service - Installation](/tutorials/webservice-installation).

## Authoring API

Beyond executing published dialogues, the Web Service also hosts the API used by the visual dialogue editor in [Dialogue Branch Studio](/web-services/studio):

* **Projects** (`/project/*`) — create, update, delete Dialogue Branch Projects and manage their translation languages.
* **Authoring** (`/authoring/*`) — CRUD operations on a project's editable *draft* dialogues, nodes and translations. Draft content is kept separate from published content, so authors can make in-progress changes without affecting what's currently live.
* **Publishing** (`/publish/*`) — validate and publish a project's current draft content as a new, immutable published version, available to the execution engine.
* **Draft execution** (`/draft/*`) — lets an author test-run a dialogue against its unpublished draft content, separate from the normal `/dialogue/*` execution path against published content.

After having successfully deployed the web service, you can start exploring its functionalities through the provided [Swagger](https://swagger.io/) pages (see image below).

![Screenshot of the provided Swagger pages for the Dialogue Branch Web Service.](/images/dlb-web-swagger.png)

*Screenshot of the provided Swagger pages for the Dialogue Branch Web Service.*

## Dialogue Execution

The Web Service offers a `/dialogue/*` family of end-points for starting and progressing dialogues: `/dialogue/start`, `/dialogue/progress`, resuming an interrupted session (`/dialogue/continue`, `/dialogue/get-ongoing`), reverting to a previous step (`/dialogue/back`), explicitly ending a session (`/dialogue/cancel`), and (for users with the `editor` or `admin` role) listing all dialogues available in a project (`/dialogue/list-dialogues`). See the Swagger UI for the full set of parameters for each end-point.

For a concrete, step-by-step example of calling these end-points from your own client application, see [3rd Party Client Applications](/web-services/third-party-clients).

## Working with Variables

Variables are used in .dlb scripts to create dynamic dialogue flow, and include flavourful personalisations. These Variables can be set and used inside the dialogue scripts themselves, as in the example below:

```text
<<set $playerName = "Bob">>

Hello $playerName, how are you doing?

[[I'm fine.|PlayerIsFine]]
[[I'm sad.|PlayerIsSad]]
```

However, as in the example, it doesn't always make sense to set the values for Variables in the dialogue scripts themselves. Instead, these values might originate from another part of your client application. Imagine that your client application is a game that includes a user interface where players can insert their name. When a player does this, the value should be communicated to Dialogue Branch, so that the $playerName variable may be used in dialogues.

The Web Service offers the following end-points for sending Variable-values to the service:

* `/variables/set-single` — allowing you to set the value for a single Variable by providing a `name` and a `value`.
* `/variables/set` — allowing you to set the value for a number of Variables simultaneously by including a JSON payload in the body.

Using these, you can inform Dialogue Branch about Variables whose values are generated through any part of your client application. The other way around, your client application can also ask the Web Service about Variable values, using the following end-point:

* `/variables/get` — allows you to ask for all known Variables for a user, or a space-separated list of specific Variables (via the `variableNames` parameter).

Another way of making sure that Dialogue Branch has up-to-date values for Variables, is by using an [External Variable Service](/web-services/external-variable-service).

::: info Note
If you found errors or have questions about this page, please consider reporting an issue at https://github.com/dialoguebranch/platform or sending an email to info@dialoguebranch.com.
:::

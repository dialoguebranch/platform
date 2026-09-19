# Dialogue Branch client-js (`@dialoguebranch/client-js`)

A JavaScript client for talking to a [Dialogue Branch](https://www.dialoguebranch.com) Web
Service. No framework dependency (no Vue, no `document`/browser assumptions) — it runs in a
browser, Node, or (in principle) React Native.

Two classes, not one:

- **`DialogueBranchClient`** (this package's default export) — playback only: run a published
  dialogue, read/set the current user's variables. What an end-user-facing app (a chatbot front
  end, a game's dialogue UI, anything that just plays dialogues) needs.
- **`DialogueBranchAuthoringClient`** (`@dialoguebranch/client-js/authoring`) — project and
  dialogue authoring: project CRUD, publishing, node/translation editing, testing unpublished
  draft content. This is what [Dialogue Branch Studio](../../apps/studio) itself is built on; a
  playback-only consumer never needs it.

See [dialoguebranch/platform#231](https://github.com/dialoguebranch/platform/issues/231) for why
these are deliberately kept separate.

## Install

Not yet published to npm (tracked separately as
[#230](https://github.com/dialoguebranch/platform/issues/230)). Within this monorepo, `apps/studio`
consumes it as a local `file:` dependency — the same pattern works for another package in this
repo:

```json
{
  "dependencies": {
    "@dialoguebranch/client-js": "file:../../packages/client-js"
  }
}
```

## Quickstart (playback)

```js
import { DialogueBranchClient } from '@dialoguebranch/client-js';
import { BasicReply } from '@dialoguebranch/client-js/model/BasicReply';

const client = new DialogueBranchClient({ baseUrl: 'https://your-web-service/v1' });

// Start a dialogue, then render it.
let step = await client.startDialogue('my-project', 'welcome', 'en');
render(step);

function render(step) {
  // step.statement is a Statement — fullStatement() gives the plain-text rendering of its
  // TEXT segments (segments also exposes INPUT/ACTION parts, for a richer UI).
  console.log(step.statement.fullStatement());
  // step.replies is an array of Reply (BasicReply carries its own statement; AutoForwardReply
  // doesn't — it's a reply with no user-visible text, e.g. a "Continue" button).
  for (const reply of step.replies) {
    console.log(reply.replyId, reply instanceof BasicReply ? reply.statement.fullStatement() : '(auto-forward)');
  }
}

// When the user picks a reply, progress the dialogue with its replyId.
async function onReplySelected(reply) {
  step = await client.progressDialogue(step.loggedDialogueId, step.loggedInteractionIndex, reply.replyId);
  if (step) render(step); else console.log('Dialogue ended.');
}
```

The constructor also accepts `fetch`, `credentials`, `onRequest`, `onApiCall`, and `onUnauthorized`
— see the JSDoc on `BaseClient`'s constructor for what each does. None are required for the common
case of a browser app talking to a same-origin (or CORS-enabled) Web Service.

## Exports

| Subpath | What |
|---|---|
| `@dialoguebranch/client-js` | `DialogueBranchClient` — playback (default export) |
| `@dialoguebranch/client-js/authoring` | `DialogueBranchAuthoringClient` — project/dialogue authoring |
| `@dialoguebranch/client-js/protocol` | Side-effect-free re-export of the `model/` wire-protocol types, no transport code — for sharing types without pulling in either client class or `fetch` |
| `@dialoguebranch/client-js/ClientState` | Reusable, product-neutral session state |
| `@dialoguebranch/client-js/model/*` | Individual wire-protocol types (`Reply`, `Segment`, `Action`, etc.) |
| `@dialoguebranch/client-js/util/*` | `AbstractLogger`, `ConsoleLogger` |

## Development

```bash
npm install
npm test          # run tests once
npm run test:watch
```

No build step — `exports` in `package.json` points straight at the source `.js` files.

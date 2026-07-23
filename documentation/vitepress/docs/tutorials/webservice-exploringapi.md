# Tutorial: Dialogue Branch Web Service - Exploring the API

The Dialogue Branch Web Service is a [JAVA Spring Boot Application](https://spring.io/projects/spring-boot) that can be deployed as a web service. It acts as a wrapper around the [Dialogue Branch Core Java Library](/core-java/), offering an API that allows you to create client-server dialogue applications.

This tutorial assumes that you've successfully installed the Web Service and created a test user, e.g. by following the [Tutorial: Dialogue Branch Web Service - Installation](/tutorials/webservice-installation).

## Exploring the API

To get started, open a web browser and navigate to your local Web Service's Swagger UI (e.g. `http://localhost:8089/dlb-web-service/swagger-ui/index.html`). You should see something like the image below:

![If deployed correctly, this is what you should be seeing - the Swagger API Documentation of the Dialogue Branch Web Service.](/images/webservice-setup-tutorial-1-swagger.png)

*If deployed correctly, this is what you should be seeing - the Swagger API Documentation of the Dialogue Branch Web Service.*

### Authenticating

All API end-points (other than `/info/all` and a few infrastructure end-points) require a valid [JSON Web Token](https://jwt.io/) (JWT), issued by Keycloak, in the `Authorization: Bearer <token>` header of every call — see [Authentication](/web-services/authentication) for the full picture. The bundled Swagger UI is itself registered as a Keycloak client, so you can authenticate without writing any client code:

* Scroll to the top of the Swagger page and click the green **Authorize** button in the top right corner.
* In the dialog that appears, you'll be redirected to Keycloak's own login page (or shown a login form inline, depending on your browser pop-up settings).
* Log in with the test user you created while following the installation tutorial (e.g. `testuser`).
* After a successful login, Swagger stores the resulting access token and automatically attaches it to every request you make from this point on — click **Close** to return to the API list.

::: info Note
Which end-points you're allowed to call, and whether you can specify a `delegateUser` parameter to act on behalf of another user, depends on which of the `participant`/`editor`/`admin` roles are assigned to your test user — see [Authentication](/web-services/authentication).
:::

### Summary of API End-Points

Before we can start building our own software client that can communicate with the Web Service, we will explore the functionalities of the API using the Swagger UI.

* Unfold **2. Dialogue** to reveal the following end-points:
  * **/dialogue/start** — Starts the interaction between user and system for a specific dialogue in a specific project.
  * **/dialogue/progress** — Progresses the dialogue a step forward by providing a specific `replyId`.
  * **/dialogue/continue** — This will "pick up the conversation" by providing a project and dialogue name, and resuming any ongoing dialogue where it was left off.
  * **/dialogue/back** — This will cause the dialogue to go one step back.
  * **/dialogue/cancel** — This will cancel an ongoing dialogue, discarding its current state.
  * **/dialogue/get-ongoing** — For a given project, provides information on the most recent ongoing dialogue (if any).
  * **/dialogue/list-dialogues** — Lists all dialogues available in a project (requires the `editor` or `admin` role).
* Unfold **3. Variables** to find `/variables/get`, `/variables/set-single` and `/variables/set`, for reading and writing a user's Dialogue Branch Variables.

The execution of a Dialogue Branch dialogue between a client and the Web Service is thus a series of API calls, starting with **start**, followed by a series of **progress** (and optionally **back**) calls, that either ends with a call to **cancel** or by the dialogue naturally ending.

## Running a basic dialogue

We are going to walk through all the possible steps in a client-server Dialogue Branch application. As an example, we will be using the `basic.dlb` dialogue from the `default-test` project, which the Web Service seeds automatically on first startup (see the installation tutorial). The `basic.dlb` dialogue is a very simple dialogue that demonstrates how dialogues start, progress and end.

In the steps below, we're going to show the series of API calls a client might take to execute this dialogue step-by-step:

### The beginning (/dialogue/start)

* Unfold the **/dialogue/start** endpoint and click on "*Try it out*". There are six parameters that can be entered at this point:
  * `projectSlug` — The project that contains the dialogue we want to start, this should be "**default-test**".
  * `dialogueName` — The name of the dialogue that we want to start, this should be "**basic**" (the extension is not part of the dialogue name).
  * `language` — For now we choose "**en**", and we deal with translations later.
  * `timeZone` — The current timezone as a location-based identifier from the tz database. For example *Europe/Lisbon*.
  * (optional) `delegateUser` — If your test user has the `admin` role, use this to specify the "real" end-user for which to start the dialogue. Leave empty for now.
  * (optional) `sessionId` — An optional identifier that is attached to the dialogue logs, allowing this dialogue session to be cross-referenced with external logs. Leave empty for now.
* When you've filled in "**default-test**" as `projectSlug`, "**basic**" as `dialogueName`, "**en**" as `language` and any valid `timeZone`, you can click "*Execute*", resulting in a JSON Response similar to the following:

*Code Block: JSON Response #1.*

```json
{
  "dialogue": "basic",
  "node": "Start",
  "loggedDialogueId": "bf758d37cef84ff3a5ef818abce2f6c0",
  "loggedInteractionIndex": 0,
  "speaker": "Martin McOwl",
  "statement": {
    "segments": [
      {
        "segmentType": "TEXT",
        "text": "Hi, my name is Martin McOwl, and this is the Default Dialogue Branch Test dialogue."
      }
    ]
  },
  "replies": [
    {
      "replyId": 1,
      "statement": {
        "segments": [
          {
            "segmentType": "TEXT",
            "text": "Nice to meet you Martin McOwl."
          }
        ]
      },
      "actions": [],
      "endsDialogue": false
    },
    {
      "replyId": 2,
      "statement": {
        "segments": [
          {
            "segmentType": "TEXT",
            "text": "Goodbye."
          }
        ]
      },
      "actions": [],
      "endsDialogue": true
    }
  ]
}
```

When you are building a Client application, you need to extract this JSON response and construct your user interface around it. For now, it suffices to understand that this JSON response contains the `speaker` ("Martin McOwl"), the `statement` ("*Hi, my name is Martin McOwl, and this is the Default Dialogue Branch Test dialogue.*") and two `reply` options: (1) "*Nice to meet you Martin McOwl.*", or (2) "*Goodbye.*". Furthermore, the JSON response contains some meta information that we will need to progress the dialogue: the `loggedDialogueId` (e.g. `bf758d37cef84ff3a5ef818abce2f6c0`) and the `loggedInteractionIndex` (`0`).

### Selecting a reply (/dialogue/progress)

* Next, we want to advance the dialogue by sending the reply "*Nice to meet you Martin McOwl.*". For this we will unfold the **/dialogue/progress** end-point, and click on "*Try it out*". Besides the optional `delegateUser` parameter explained earlier, there are three parameters for this call:
  * `loggedDialogueId` — This is the identifier of the current dialogue instance that you've obtained from the previous call to `/dialogue/start`, `/dialogue/progress` or `/dialogue/back`. We fill in the value obtained earlier: `bf758d37cef84ff3a5ef818abce2f6c0`.
  * `loggedInteractionIndex` — This is the index of the current interaction in the dialogue, as obtained in the previous call. We fill in: `0`.
  * `replyId` — The ID of the selected reply. Since we want to say "*Nice to meet you Martin McOwl.*", we fill in `1` (note that the first `replyId` is "1" and not "0" as is common in listings).
* When you've filled in everything you can click "*Execute*", resulting in a JSON Response similar to the following:

*Code Block: JSON Response #2.*

```json
{
  "value": {
    "dialogue": "basic",
    "node": "Continue",
    "loggedDialogueId": "bf758d37cef84ff3a5ef818abce2f6c0",
    "loggedInteractionIndex": 2,
    "speaker": "Martin McOwl",
    "statement": {
      "segments": [
        {
          "segmentType": "TEXT",
          "text": "This dialogue is very basic, and shows only dialogue flow and ending."
        }
      ]
    },
    "replies": [
      {
        "replyId": 1,
        "statement": null,
        "actions": [],
        "endsDialogue": false
      }
    ]
  }
}
```

This response is very similar to the previous one (JSON Response #1). The notable difference is that there is only 1 `reply` option, and its `statement` field is empty (`null`). This is what is called an "*Auto-forward Reply*" (see the [Dialogue Branch Language](/language/) doc page), and the idea is that the dialogue can "automatically move forward" after some time, or after the user pressed a button (e.g. "Continue").

### Auto-Forward (/dialogue/progress)

* Make an additional call to the **/dialogue/progress** end-point with `loggedDialogueId: bf758d37cef84ff3a5ef818abce2f6c0`, `loggedInteractionIndex: 2`, and `replyId: 1`. This will progress the dialogue to its next node.

### Taking a step back (/dialogue/back)

Let's assume the previous "Auto-forward Reply" advanced the dialogue a bit too quickly for the liking of our end-user. In that case, a UI designer could offer a "Back" button, that, when pressed, calls the **/dialogue/back** end-point.

* Unfold the **/dialogue/back** end-point and click on "*Try it out*".
* Fill in the `loggedDialogueId` and the `loggedInteractionIndex` from the response you got in the previous step, and press "*Execute*".

The response you get will be exactly the same as the one from the previous step. Essentially, the Web Service looks up the previously returned result and returns it again. If there is no previous step, it would just return the current step.

### Picking up the Conversation (/dialogue/continue)

If for whatever reason, you have lost the current "state" of your user interface (e.g. the user logged out, the browser was closed, or your application has crashed - it happens!), you can use the **/dialogue/continue** end-point to retrieve the latest state of a given dialogue. So, if you know that your user was interacting with the `basic` dialogue in the `default-test` project, but you've lost track of where you were, you can call:

* **/dialogue/continue** with `projectSlug` as `default-test`, `dialogueName` as `basic` and `timeZone` as any valid input.

This call will return exactly the current state of the conversation, wrapped the same way as JSON Response #2 above.

### Where was I? (/dialogue/get-ongoing)

In the unfortunate event that you (or your client) has forgotten whether or not there was an ongoing conversation, there is another method to recover: the `/dialogue/get-ongoing` end-point. Provide the `projectSlug` and a `timeZone` (besides the optional `delegateUser`), and it will answer the question "What was the latest ongoing dialogue in this project?". An "ongoing" dialogue is any dialogue that has not been specifically cancelled (by a call to `/dialogue/cancel`) and hasn't ended naturally, and whose project hasn't been republished in the meantime. Making this call through Swagger now will return something like this:

*Code Block: JSON Response #3.*

```json
{
  "value": {
    "dialogueName": "basic",
    "loggedDialogueId": "bf758d37cef84ff3a5ef818abce2f6c0",
    "secondsSinceLastEngagement": 11
  }
}
```

Based on the value of `secondsSinceLastEngagement` you may decide to continue this conversation through the `/dialogue/continue` end-point.

### Ending the dialogue (/dialogue/progress)

Continue selecting replies (via repeated `/dialogue/progress` calls, using the `loggedDialogueId`/`loggedInteractionIndex` from each previous response) until you reach a reply whose `endsDialogue` field is `true`, and select it. As this is the end of the dialogue, the response will not contain anything useful. To be exact, this is what you can expect the Dialogue Branch Web Service to return:

*Code Block: JSON Response #4.*

```json
{
  "value": null
}
```

When building a User Interface, you use the `endsDialogue` flag on a reply in two ways: first, when the user selects such a reply, you don't have to wait for a meaningful response from the server (as we just saw); second, you might wish to indicate to the user up front that selecting this reply option will end the conversation, for example by adding an "Exit Icon" to the reply button.

### Cancelling dialogues (/dialogue/cancel)

Finally, if you (or your client app) wants to stop engaging in a certain dialogue (and not being able to pick it up later), you can choose to use the `/dialogue/cancel` end-point. This end-point simply requires the `loggedDialogueId` and will mark that dialogue's state as cancelled.

## What's next?

So now you have a running Dialogue Branch Web Service, and you understand how you can use its API to start, progress, continue and end dialogues. The next step is to move away from Swagger, and start with a proper client application — Dialogue Branch Studio (in `apps/web` of this repository) is a complete, working example of exactly that, and a good reference to read alongside `src/dlb-lib/DialogueBranchClient.js`, its thin fetch-based API client.

::: info Note
If you found errors or have questions about this tutorial, please let us know by sending an email to info@dialoguebranch.com.
:::

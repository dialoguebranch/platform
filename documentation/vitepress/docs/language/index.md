# Dialogue Branch Language Definition

At its core, Dialogue Branch is similar to a programming or "scripting" language. The Dialogue Branch Language is defined by a set of rules that dictates what a validly specified Dialogue Branch Dialogue is. It tells you how to specify *Statements* that can be uttered by *Agents*, and how to define various *Reply* options.

The sections below describe all of the different concepts in a Dialogue Branch script, starting from the basics, and dealing progressively with more advanced topics.

## Basics & Terms

A Dialogue Branch script is essentially a definition of a series of dialogue steps (that we refer to as *Node*s) linked together through user replies.

We define the following terms:

* Node — A dialogue step that contains one Statement and a one or more Replies.
* Statement — Something an agent says.
* Reply — A possible reply that a user of the system can give.
* Agent — A virtual speaker within a dialogue.

## Nodes

A *Node* consists of two parts, a header, and a body.

### Header

The header consists of a series of lines, each with a `key: value`-pair. The two required key-value pairs are:

* `title` — a String matching `[A-Za-z0-9_-]+` that uniquely identifies this *Node* within this dialogue (matching is case-insensitive).
* `speaker` — a String that defines the name of the *Agent* speaking in this *Node*. Required for every node, **except** a node titled [`End`](#ending-a-dialogue), whose speaker is ignored even if present.

You are free to define other key-value pairs that might serve as meta-data in your application. The visual editor in the web client (see [Dialogue Branch Web Services](/web-services/)) additionally recognises the following reserved tags:

* `colorId` — a number specifying a colour used by the node-graph editor to visually group or distinguish nodes.
* `position` — a pair of `x,y` coordinates (comma-separated integers) used to position the node on the editor's canvas. Malformed values silently default to `0,0`.

::: info Note
The reserved tag is spelled `colorId` (lowercase `d`). Some older example content in this repository still uses the legacy spelling `colorID` — the parser accepts either (any header key you write is stored as-is), but only `colorId` is recognised by the visual editor.
:::

You may also see an empty `tags:` line in existing `.dlb` files. This key is not currently interpreted by the parser or the editor; it is reserved for future use.

Below is an example of what this looks like in a .dlb file:

```text
title: Start
speaker: Robin
colorId: 3
position: -416,112
```

### Body

The body of a *Node* contains at least one *Statement* and zero or more *Reply*'s. A very basic example is given below:

```text
Hello, my name is Robin!

[[Nice to meet you Robin!|NodeRobin2]]
[[Goodbye.|NodeEnd]]
```

This **Node** defines a **Statement** "*Hello, my name is Robin!*", uttered by the **Agent** "*Robin*" and two possible **Reply** options. When a user selects "*Nice to meet you Robin!*" he will be forwarded to a **Node** labeled `NodeRobin2`, and when he selects "*Goodbye.*" he will be forwarded to the **Node** labeled `NodeEnd`.

![Example node body](/images/example-nodes-body.png)

### File Format

A `.dlb` file consists of a list of concatenated Nodes separated by `===` markers. The header and body of the **Node** are separated by the `---` line. For example:

```text
title: Start
speaker: Robin
position: -416,112
colorId: 3
---
Hello, my name is Robin!

[[Nice to meet you Robin!|NodeRobin2]]
[[Goodbye.|NodeEnd]]
===
title: NodeRobin2
speaker: Robin
position: -216,112
colorId: 4
---
Nice to meet you too, how are you doing?

[[I am fine and you?|NodeRobin3]]
[[Goodbye.|NodeEnd]]
===
...
```

A `.dlb` file does not need to be registered anywhere — any file with a `.dlb` extension found anywhere inside a language folder of a [Dialogue Branch Project](/language/dlb-project) is automatically picked up and parsed.

### Comments

If you want to document your Dialogue Branch scripts with comments, Dialogue Branch supports line-comments. Everything after a double-slash `//` is considered a comment — this works both in the header and in the body of a Node:

```text
title: Start // this is our entry point
speaker: Robin
---
Thank you very much. // Thank the user for the gift.

// If it was a nice gift...
<<if $giftNice >>
   ...
```

Pay attention when including hyperlinks in your text (e.g. https://www.dialoguebranch.com) — in this case, the first forward-slash should be escaped, as follows:

```text
Please visit https:\//www.dialoguebranch.com for more information! // Comment goes here.
...
```

## Dialogues

A series of Dialogue Branch **Nodes** is called a Dialogue. The following rules apply to Dialogues:

* All **Node** title's must be unique within a Dialogue (case-insensitive).
* There must be one **Node** with the title "*Start*" (matched case-insensitively, so `start`, `Start` or `START` all satisfy this requirement).
* Dialogue Branch script files may contain letters, numbers, dashes and underscores, and end with `.dlb`, valid examples include:
  * mydialogue.dlb
  * my-dialogue.dlb
  * my_dialogue-1.dlb
  * 123dialogue_for-Robin.dlb

### Starting a Dialogue

Every Dialogue Branch script must include a Node with title "*Start*". Applications that execute Dialogue Branch scripts can choose this as the default starting node for a conversation (or ignore it, and start somewhere else, it's your party). For example:

```text
title: Start
speaker: Robin
position: -416,112
colorId: 3
---
Hello, my name is Robin!

[[Nice to meet you Robin!|NodeRobin2]]
[[Goodbye.|NodeEnd]]
===
```

### Ending a Dialogue

There are two ways a Dialogue Branch dialogue can end:

* The user doesn't have any **Reply** options in the **Node** (the **Agent** has the last say).
* The user chooses a **Reply** option that leads to a **Node** with title "*End*" (matched case-insensitively) (user has the last say).

Example 1:

```text
It was nice talking to you, bye!
```

Example 2:

```text
Do you have any other questions?

[[I have nothing left to say.|End]]
```

Unlike the "*Start*" **Node**, the "*End*" **Node** is not mandatory to include in your Dialogue Branch script, as there are other ways to end the conversation. There is also nothing stopping you from creating a dialogue that can only loop indefinitely. When creating DLB-based applications, you can also choose to provide a User Interface element that can "cancel" a dialogue at any time.

::: info Note
The node with the title "End" is a special case: it must be entirely empty (no statement, no replies) — the parser rejects an "End" node that contains a body. When other nodes refer to it, this Node should still be created in the dialogue as usual (header only), and when the "End" node is reached in a Dialogue, the application should simply "close" the conversation.
:::

## Statements

Ultimately, every Dialogue Branch **Node** should output some text to display to the user, but DLB **Statements** allow for a lot of flexibility in structuring and personalizing your dialogue.

### Basic Statements

The most basic **Statement** is a simple line of text, that is uttered by the speaker (**Agent**) of the **Node**:

```text
Hello, how are you?
```

### Basic Statements with Variables

You can use variables within your **Statements**. Variable names must match `[A-Za-z_][A-Za-z0-9_]*` — i.e. they start with a `$`-sign, followed by a letter or underscore, then any number of letters, digits or underscores.

In short: variables names start with a `$`, then a letter or underscore, then letters, numbers or underscores, for example:

* `$variableName`
* `$variable_name`
* `$_privateVar`
* `$var123`

These variables can be used within **Statements** to inject their values into a sentence, like so:

```text
Hello $userFirstName, how are you?
```

### Basic Statements with Special Characters

What if you actually want to include a $ character in your text? If it's not followed by A-Z a-z, you can just type $. But otherwise you can escape it with a backslash: \$. And to include a backslash? Just escape it with another backslash: \\. In fact, you can escape any character with \ and it will not be treated as a special character.

### Basic Statements with Markup

For the rest, Dialogue Branch doesn't care about any markup you might want to apply, if you want to add HTML tags around text, please go ahead. The parsers will ignore it, and simply output the text including markup to your application:

```text
Hello <b>$userFirstName</b>, how are you?
```

### Control Statements: Setting Variables

Dialogue Branch allows you to set variables using the `<<set>>` statement.

```text
<<set $userFirstName = "Bob">>
<<set $points = 0>>
<<set $hasReplied = true>>
```

The example above shows the three most common cases for setting either `String`, `number` or `boolean` variables. However, Dialogue Branch is much more flexible, and allows for example the set-**Statements** below:

```text
<<set $points = $points + 1>>
<<set $name = $firstname + " " + $lastname>>
<<set $string = "String" + 12345>> // $string is set to "String12345"
<<set $string = 1 + 2 + "3">> // is parsed from left to right, $string = "33"
<<set $string = 1 + (2 + "3")>> // using brackets, resulting in $string = "123"
```

As you can see, you don't have to define the `type` of the variable manually. Be careful when using more complex statements though. For example, when trying to add up numbers with Strings, Dialogue Branch will treat the result as a String.

### Control Statements: Conditionals

Dialogue Branch supports if-then-else **Statements**. The simple example:

```text
<<if $dayPart == "Morning" >>
   Good morning ladies and gentlemen!
<<elseif $dayPart == "Afternoon" >>
   Good afternoon peoples!
<<else>>
   Good evening everyone!
<<endif>>
```

Please note that "`==`" is treated as strictly-equals.

Dialogue Branch also supports nesting these if-statements, if needed:

```text
<<if $dayPart == "Morning" >>
    <<if $userFriendly == true>>
        Good morning, sir! How are you today.
    <<else>>
        Mornin'.
    <<endif>>
<<elseif $dayPart == "Afternoon" >>
   ...
```

Note that in the case of boolean variables (`$userFriendly`), you can leave out the "` == true`" part. E.g. the following is valid and will work as expected if `$userFriendly` is an actual `boolean` value:

```text
<<if $userFriendly>>
```

However, if `$userFriendly` is actually a String with value "*No, he is not friendly.*", this expression will evaluate to `true`. If the variable is empty (or "unset"), the expression will evaluate to `false`.

Be careful with using the shorthand form `<<if $variableName>>`, because it is *not strict*, while for example `<<if $variableName == false>>` is strict. This means that if the variable `$variableName` has not been assigned a value, the following will happen:

* `<<if $variableName>>` will evaluate to `false`
* `<<if $variableName == false>>` will also evaluate to `false` (which may be counterintuitive)

If in your application you cannot be sure whether or not boolean variables have been assigned a value, our advice is to always use the *if-equals-true-else* construction:

```text
<<if $variableName == true>>
  // The variable is a boolean, it exists, and it's value is definitely true.
<<else>>
  // The variable was either false, hasn't been set, or contains some other unexpected content.
<<endif>>
```

### Control Statements: Random Variation

Dialogue Branch supports offering weighted random variation of a piece of dialogue through the `<<random>>` statement. Unlike `<<if>>`, this isn't a decision based on a condition, but a way to make a node's output vary from one execution to the next, without the client or the dialogue author having to decide up front which variant is shown.

```text
<<random>>
Hey there!
<<or>>
Hi, good to see you!
<<or>>
Hello again!
<<endrandom>>
```

Each `<<random>>` clause and each `<<or>>` clause may specify an optional `weight` (a positive number, default `1`) to bias the random selection:

```text
<<random weight="2">>
This is shown roughly twice as often as the other options.
<<or weight="1">>
This is shown about half as often as the first option.
<<or>>
This clause has the default weight of 1, same as the previous one.
<<endrandom>>
```

At runtime, exactly one of the clauses is chosen at random (proportional to its weight) and only that clause's content is executed — the others are skipped entirely. A `<<random>>` block may appear anywhere a `<<set>>` or `<<if>>` statement may appear, and each clause may itself contain nested `<<if>>`, `<<set>>` or further `<<random>>` statements. It is not valid inside the command section of a **Reply** (see [Replies](#replies) below).

### Control Statements: User Interface Actions

Sometimes you might want to couple some event or action to a statement uttered by a speaker. Dialogue Branch supports Actions of type `link`, `image`, `video`, or `generic`, all of which are shown below.

The `link` example:

```text
Check out this website for an awesome dialogue platform.
<<action type="link" value="https://www.dialoguebranch.com/">>
```

The `image` example:

```text
And here you can see a picture of a dog.
<<action type="image" value="dog.png">>
```

The `video` example:

```text
I would like to show you this cool video I found.
<<action type="video" value="https://www.youtube.com/watch?v=dQw4w9WgXcQ">>
```

The `generic` example:

```text
Let me show you something in this book I found.
<<action type="generic" value="OPEN_RECIPE_BOOK">>
```

The four examples above show the four basic cases of using `<<action>>`-**Statements**. Every **Action** requires a `type` and a `value`. The `type` must be one of `{link, image, video, generic}`. The `value` has a specific and obvious meaning for types link, image, and video. For generic actions, you are free to assign your value tag, and write your Dialogue Branch Client to handle it however you like.

Besides the `type` and `value` parameters, an **Action** may have any number of additional parameters. See some examples below:

```text
Check out this <<action type="link" value="https://www.dialoguebranch.com/" text="website">> for an awesome dialogue platform.
```

In this example, we added the `text` parameter to the action of type `link`, so that we can tell our client to render the `text`, and turn it into a hyperlink.

In the example below, we extend our generic action to pass along some additional information to our recipe book widget. In this case, we want our UI to wait 2000ms, and then open the recipe book to page 42.

```text
Let me show you something in this book I found.
<<action type="generic" value="OPEN_RECIPE_BOOK" delay="2000" page="42">>
```

## Replies

Every **Node** can define zero or more (indefinite, but please consult your UI designer) **Reply** options, the different types are defined below.

### Basic Replies

The standard **Reply** option defines a "user statement" and "Node Pointer", separated by a `|` (pipe).

```text
[[Are you sure, Robin?|NodeConfirm]]
```

The user should be forwarded to the **Node** with title `NodeConfirm` when selecting the "*Are you sure, Robin?*" option.

### Auto-forward Replies

In replies, you can leave out the statement, and just provide a **Node** Pointer, but you can only have one of these per **Node**.

```text
[[NodeConfirm]]
```

This should allow your user to go to the `NodeConfirm` node when selecting e.g. a default "Continue" button, or automatically after some time (up to your UI design). You can not have two of these options in the same Node, but you can mix them with Basic replies, like so:

```text
Would you like me to sign you up?

[[Yes, please do so!|Confirm]]
[[No, let's not.|Cancel]]
[[UserInDoubt]]
```

In the example above, you could for example give the user some time to choose between the "*Yes, please do so!*" and "*No, let's not.*" options, and after some time, automatically progress the dialogue to the `UserInDoubt` **Node**.

### Input Replies

You can ask a user to provide various types of input as part of a reply's statement text, using an `<<input>>` command. Dialogue Branch supports six input types: `text`, `longtext`, `email`, `numeric`, `time`, and `set`.

The easiest way is to request some text input:

```text
What is your first name?

[[None of your business Robin.|RobinInsulted]]
[[My name is <<input type="text" value="$userFirstName" min="2" max="30">>, why do you ask?|RobinInputGiven]]
```

The general format of this statement is: (optional) `beforeText`, `inputStatement`, (optional) `afterText`.

So, the minimum valid example is as follows:

```text
What is your first name?

[[None of your business Robin.|RobinInsulted]]
[[<<input type="text" value="$userFirstName">>|RobinInputGiven]]
```

When a user chooses the Input Reply, the provided text is assigned to the value of the `$userFirstName` variable.

Every input type accepts an optional `description` parameter (e.g. as a hint or accessibility label for a client's UI), in addition to the type-specific parameters described below.

#### `text` and `longtext`

Free-form text input (`longtext` is intended for multi-line input; both share the same parameters).

* `value` — (required) the variable to assign the input to.
* `min`, `max` — (optional) minimum/maximum input length.
* `allowNumbers`, `allowSpecialCharacters`, `allowSpaces` — (optional, `true`/`false`) restrict which characters are allowed.
* `capCharacters`, `capWords`, `capSentences` — (optional, `true`/`false`) hint to the client UI to auto-capitalize characters/words/sentences as the user types.
* `forceCapCharacters`, `forceCapWords`, `forceCapSentences` — (optional, `true`/`false`) enforce that capitalisation server-side.

#### `email`

```text
[[My email address is <<input type="email" value="$userEmail">>.|RobinInputGiven]]
```

* `value` — (required) the variable to assign the input to.

#### `numeric`

```text
[[I am <<input type="numeric" value="$userAge" min="0" max="120">> years old.|RobinInputGiven]]
```

* `value` — (required) the variable to assign the input to.
* `min`, `max` — (optional) minimum/maximum numeric value.

#### `time`

```text
[[I ate my breakfast at <<input type="time" value="$breakfastTime" granularityMinutes="15" startTime="09:00" minTime="06:00" maxTime="12:00">> this morning.|BreakfastTimeGiven]]
```

This results in the variable `$breakfastTime` being set to the provided input (e.g. "07:45"). There are four optional parameters:

* `granularityMinutes` — In the example this is 15, meaning that you can enter 00, 15, 30 or 45. You can use any value between 1 and 60. The default is 1.
* `startTime` — The time that the input widget should show initially. For example "09:00", but you can also write "now", or even a variable: "`$breakfastTimeYesterday`". If you leave it out, then the input widget will start empty.
* `minTime` — The minimum time that the user can enter. The default is "00:00".
* `maxTime` — The maximum time that the user can enter. The default is "23:59".

#### `set`

Presents the user with a set of labelled boolean options (e.g. checkboxes), each toggling its own variable, using numbered `valueN`/`optionN` pairs:

```text
Which toppings would you like?
<<input type="set" value1="$wantsCheese" option1="Cheese" value2="$wantsOlives" option2="Olives" value3="$wantsHam" option3="Ham">>
```

Each `valueN` is a `$variable` that will be set to `true`/`false` depending on whether the user selected `optionN`'s label.

### Replies with Setting Variables

Instead of setting a variable in a single set-**Statement**, you can also set a variable as part of a **Reply** option, like in the following example:

```text
Do you prefer meat or fish?

[[Meat please.|NodeMeat|<<set $likesMeat = true>>]]
[[Fish for me.|NodeFish|<<set $likesFish = true>>]]
```

### Replies with Actions

Just like you are able to link a set-**Statement** to a **Reply**, you can also add action-**Statements** to replies, like so:

```text
[[Please show me the recipes.|RecipesStart|<<action type="generic" value="OPEN_RECIPE_BOOK">>]]
```

::: info Note
The optional third section of a **Reply** (after the second `|`) may only contain `<<set>>` and/or `<<action>>` statements — `<<if>>` and `<<random>>` are not valid there. If you need conditional logic around a reply, express it in the **Node**'s body instead (e.g. by using `<<if>>` to decide which replies to show).
:::

### Replies linking to other dialogues

There's only so much you want to put into one Dialogue Branch dialogue definition before you start losing track (and/or sanity), so Dialogue Branch allows you to link between different dialogue definitions, like so:

```text
What should we talk about now?

[[Know anything about cars?|CarsDialogue.Start]]
[[What about fishing?|FishingDialogue.Start]]
```

In this example, the first **Reply** option would take the user to the **Node** labeled "*Start*" of the Dialogue labeled "*CarsDialogue*". So, in this case, there should be a file named "*CarsDialogue.dlb*" in the same folder as the current .dlb script.

The path to the target dialogue is resolved relative to the current dialogue's location inside the project's language folder, and supports filesystem-like path segments, similar to a relative file path:

```text
[[Same folder|basic.Start]]
[[Absolute path from the language root|/bg1/bg1-candlekeep-imoen.Start]]
[[Parent folder|../menu.Start]]
[[Explicit current folder|./basic.Start]]
```

Note that you don't *have to* link to the "Start" **Node** of a dialogue script, and you can choose any valid **Node** name.

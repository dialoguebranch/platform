import { WCTAInteractionRenderer } from './WCTAInteractionRenderer.js';
import { AutoForwardReply } from '../dlb-lib/model/AutoForwardReply.js';

export class WCTABalloonsRenderer extends WCTAInteractionRenderer {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    constructor(controller) {
        super(controller);
        this.rendererVisible = false;
    }

    // ---------------------------------------
    // ---------- Getters & Setters ----------
    // ---------------------------------------

    get contentBlock() {
        return this._contentBlock;
    }

    set contentBlock(contentBlock) {
        this._contentBlock = contentBlock;
    }

    get avatarBlock() {
        return this._avatarBlock;
    }

    set avatarBlock(avatarBlock) {
        this._avatarBlock = avatarBlock;
    }

    get statementBlock() {
        return this._statementBlock;
    }

    set statementBlock(statementBlock) {
        this._statementBlock = statementBlock;
    }

    get cancelDialogueButton() {
        return this._cancelDialogueButton;
    }

    set cancelDialogueButton(cancelDialogueButton) {
        this._cancelDialogueButton = cancelDialogueButton;
    }

    get repliesBlock() {
        return this._repliesBlock;
    }

    set repliesBlock(repliesBlock) {
        this._repliesBlock = repliesBlock;
    }

    get rendererVisible() {
        return this._rendererVisible;
    }

    set rendererVisible(rendererVisible) {
        this._rendererVisible = rendererVisible;
    }


    // ---------- Dialogue Step Rendering ----------

    /**
     * Make a clean sheet for the Balloons Text renderer, clearing out and hiding all relevant elements.
     */
    clear() {
        if(this.avatarBlock != null) {
            this.avatarBlock.style.backgroundImage="";
            this.avatarBlock.title = "";
            this.avatarBlock.style.visibility = "hidden";
        }
        
        if(this.statementBlock != null) {
            this.statementBlock.innerHTML = "";
            this.statementBlock.style.visibility = "hidden";
        }

        if(this.cancelDialogueButton != null) {
            this.cancelDialogueButton.style.visibility = "hidden";
        }

        if(this.repliesBlock != null) {
            this.repliesBlock.innerHTML = "";
        }
    }

    hide() {
        this.rendererVisible = false;

        if(this.contentBlock != null) this.contentBlock.style.visibility = "hidden";
        if(this.avatarBlock != null) this.avatarBlock.style.visibility = "hidden";
        if(this.statementBlock != null) this.statementBlock.style.visibility = "hidden";
        if(this.cancelDialogueButton != null) this.cancelDialogueButton.style.visibility = "hidden";
        if(this.repliesBlock != null) this.repliesBlock.style.visibility = "hidden";
    }

    unhide() { 
        this.rendererVisible = true;

        if(this.contentBlock != null) this.contentBlock.style.visibility = "visible";
        if(this.avatarBlock != null) this.avatarBlock.style.visibility = "visible";
        if(this.statementBlock != null) {
            if(this.statementBlock.innerHTML != "") {
                this.statementBlock.style.visibility = "visible";
            }   
        }
        if(this.cancelDialogueButton != null) this.cancelDialogueButton.style.visibility = "visible";
        if(this.repliesBlock != null) this.repliesBlock.style.visibility = "visible";
    }

    initialize() {

        // Set the pointer to the main balloons interaction container
        if(this.contentBlock == null) {
            this.contentBlock = document.getElementById("interaction-tester-content-balloons");
        }
        this.contentBlock.innerHTML = "";

        // Create the statement Block element if it doesn't exist yet
        if(this.statementBlock == null) { 
             this.statementBlock = document.createElement("div");
             this.statementBlock.classList.add("int-balloon-statement-balloon");
        }
        
        if(this.cancelDialogueButton == null) {
            this.cancelDialogueButton = document.createElement("button");
            this.cancelDialogueButton.innerHTML = "<i class=\"fa-solid fa-circle-xmark\"></i>";
            this.cancelDialogueButton.classList.add("int-balloon-cancel-dialogue-button");
            this.cancelDialogueButton.classList.add("circle-button-small");
            this.statementBlock.appendChild(this.cancelDialogueButton);
        }
        
        this.contentBlock.appendChild(this.statementBlock);

        // Create the avatar block if it doesn't exist
        if(this.avatarBlock == null) {
            this.avatarBlock = document.createElement("div");
            this.avatarBlock.classList.add("int-balloon-avatar");
        }
        this.contentBlock.appendChild(this.avatarBlock);

        // Create the replyContainer, or empty it if it already existed
        if(this.repliesBlock == null) {
            this.repliesBlock = document.createElement("div");
            this.repliesBlock.classList.add("int-balloon-replies-block");
        }
        this.contentBlock.appendChild(this.repliesBlock);

    }

    /**
     * Render a step in the dialogue given the information provided in the given dialogueStep object, or
     * render a "Dialogue Finished" statement if the given dialogueStep is null.
     *
     * @param {DialogueStep} dialogueStep the {@link DialogueStep} object to render, or null
     * @param {String} nullMessage the message explaining why dialogueStep is null.
     */
    renderDialogueStep(dialogueStep, nullMessage) {

        console.log("Balloons Renderer asked to render the following dialogueStep: ");
        console.log(dialogueStep);

        // Make sure all main elements exist
        this.initialize();
        
        if(dialogueStep == null) {

            // Clear all the interactive elements
            this.clear();
            
            // Create and show a message why the dialogue has ended
            var dialogueOverElement = document.createElement("div");
            dialogueOverElement.classList.add("dialogue-finished-statement");
            if(nullMessage != null) {
                dialogueOverElement.innerHTML = nullMessage;
            } else {
                dialogueOverElement.innerHTML = "Dialogue Finished";
            }
            this.contentBlock.appendChild(dialogueOverElement);
            
        } else {

            // Render the correct Agent Avatar
            this.renderAgentAvatar(dialogueStep.speaker);

            // Add the statement text to the balloon element (and make it visible)
            var statementTextElement = document.createElement("span");
            statementTextElement.classList.add("int-balloon-statement-balloon-text");
            statementTextElement.innerHTML = dialogueStep.statement.fullStatement();
            this.statementBlock.innerHTML = "";
            this.statementBlock.appendChild(statementTextElement);

            this.cancelDialogueButton.addEventListener("click", this.controller.actionCancelDialogue.bind(this.controller, dialogueStep.loggedDialogueId), false);

            this.statementBlock.appendChild(this.cancelDialogueButton);
            if(this.rendererVisible) {
                this.statementBlock.style.visibility = "visible";
                this.cancelDialogueButton.style.visibility = "visible";
            }

            // If there are any reply options
            this.repliesBlock.innerHTML = "";

            if(dialogueStep.replies.length > 0) { 
                
                let replyNumber = 1;

                dialogueStep.replies.forEach(
                    (reply) => {

                        const replyOptionContainer = document.createElement("div");
                        replyOptionContainer.classList.add("dialogue-step-reply-option-container");
                        this.repliesBlock.appendChild(replyOptionContainer);

                        if(reply instanceof AutoForwardReply) {
                            const autoForwardReplyButton = document.createElement("button");
                            autoForwardReplyButton.classList.add("dialogue-step-reply-button");
                            autoForwardReplyButton.classList.add("dialogue-step-reply-button-autoforward");
                            autoForwardReplyButton.classList.add("reply-option-with-listener");
                            if(reply.endsDialogue) {
                                autoForwardReplyButton.innerHTML = "<i class='fa-solid fa-ban'></i> END DIALOGUE";
                            } else {
                                autoForwardReplyButton.innerHTML = "CONTINUE";
                            }
                            autoForwardReplyButton.addEventListener("click", this.controller.actionSelectReply.bind(this.controller, replyNumber, reply, dialogueStep), false);
                            replyOptionContainer.appendChild(autoForwardReplyButton);
                            this.controller.dialogueReplyElements.push(autoForwardReplyButton);
                            // Add an empty element to the list, so that adding the 'user-selected-reply' class won't break.
                            this.controller.dialogueReplyNumbers.push(document.createElement("div"));
                        } else {
                            const basicReplyButton = document.createElement("button");
                            basicReplyButton.classList.add("dialogue-step-reply-button");
                            basicReplyButton.classList.add("dialogue-step-reply-button-basic");
                            basicReplyButton.classList.add("reply-option-with-listener");
                            if(reply.endsDialogue) {
                                basicReplyButton.innerHTML = "<i class='fa-solid fa-ban'></i> " + reply.statement;
                            } else {
                                basicReplyButton.innerHTML = reply.statement;
                            }
                            basicReplyButton.addEventListener("click", this.controller.actionSelectReply.bind(this.controller, replyNumber, reply, dialogueStep), false);
                            replyOptionContainer.appendChild(basicReplyButton);
                            this.controller.dialogueReplyElements.push(basicReplyButton);
                            this.controller.dialogueReplyNumbers.push(basicReplyButton);
                        }
                        replyNumber++;
                    }
                    
                );
            } else {
                // In case there are no reply options, add the "Dialogue Finished" message
                dialogueOverElement = document.createElement("div");
                dialogueOverElement.classList.add("dialogue-finished-statement");
                dialogueOverElement.innerHTML = "Dialogue Finished";
                this.repliesBlock.appendChild(dialogueOverElement);
            }

        }

    }

    renderAgentAvatar(speaker) {

        var img = new Image();
        img.src = "img/avatar-green.png";
        
        if(speaker == "Martin McOwl") {
            this.avatarBlock.style.backgroundImage="url(img/avatar-martin.png)";
        } else {
            // Todo: get a random avatar for any new speaker name we encounter (and then store it)
            this.avatarBlock.style.backgroundImage="url(img/avatar-green.png)";
        }

        this.avatarBlock.title = speaker;
        if(this.rendererVisible) this.avatarBlock.style.visibility = "visible";
    }

}
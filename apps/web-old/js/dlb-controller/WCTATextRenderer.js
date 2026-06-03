import { AutoForwardReply } from '../dlb-lib/model/AutoForwardReply.js';
import { WCTAInteractionRenderer } from './WCTAInteractionRenderer.js';

export class WCTATextRenderer extends WCTAInteractionRenderer {

    // ------------------------------------
    // ---------- Constructor(s) ----------
    // ------------------------------------

    constructor(controller) {
        super(controller);
    }

    // ---------- Dialogue Step Rendering ----------

    clear() {
        document.getElementById("interaction-tester-content-text").innerHTML = "";
    }

    /**
     * Render a step in the dialogue given the information provided in the given dialogueStep object, or
     * render a "Dialogue Finished" statement if the given dialogueStep is null.
     *
     * @param {DialogueStep} dialogueStep the {@link DialogueStep} object to render, or null
     * @param {String} nullMessage the message explaining why dialogueStep is null.
     */
    renderDialogueStep(dialogueStep, nullMessage) {

        // Remove the previous temporary filler element
        const element = document.getElementById("temp-dialogue-filler");
        if(element != null) element.remove();

        // Remove previous click event listeners
        if(this.controller.dialogueReplyElements.length > 0) {
            for(var i=0; i<this.controller.dialogueReplyElements.length; i++) {
                // Replace the node with a clone, which removes all (bound) event listeners
                this.controller.dialogueReplyElements[i].classList.remove("reply-option-with-listener");
                this.controller.dialogueReplyElements[i].replaceWith(this.controller.dialogueReplyElements[i].cloneNode(true));
            }
            // Finally, empty the set of dialogueReplyElements
            this.controller.dialogueReplyElements = new Array();
            this.controller.dialogueReplyNumbers = new Array();
        }

        var contentBlock = document.getElementById("interaction-tester-content-text");
        
        // Create the container element for the Statement
        const statementContainer = document.createElement("div");
        statementContainer.classList.add("dialogue-step-statement-container");
        contentBlock.appendChild(statementContainer);

        if(dialogueStep == null) {
            
            // Create and show a message why the dialogue has ended
            var dialogueOverElement = document.createElement("div");
            dialogueOverElement.classList.add("dialogue-finished-statement");
            if(nullMessage != null) {
                dialogueOverElement.innerHTML = nullMessage;
            } else {
                dialogueOverElement.innerHTML = "Dialogue Finished";
            }
            statementContainer.appendChild(dialogueOverElement);
            
            // Create a filler element that fills the "rest" of the scrollable area, to allow a proper
            // scrolling to the top (this element will be removed when rendering the next dialogue step)
            const fillerElement = document.createElement("div");
            fillerElement.setAttribute("id","temp-dialogue-filler");
            fillerElement.classList.add("dialogue-step-filler-element");
            contentBlock.appendChild(fillerElement);

            var contentBlockHeight = contentBlock.getBoundingClientRect().height;
            var statementContainerHeight = statementContainer.getBoundingClientRect().height;
            
            // Set the calculated height of the temporary filler element
            fillerElement.style.height = ((contentBlockHeight - statementContainerHeight) + "px");
            
            // Scroll to the top of scrollable element
            contentBlock.scrollTop = fillerElement.offsetTop;
        } else {
            // Add the speaker to the statement container
            const speakerElement = document.createElement("div");
            speakerElement.classList.add("dialogue-step-speaker");
            speakerElement.innerHTML = dialogueStep.speaker + ":";
            statementContainer.appendChild(speakerElement);

            // Add the statement to the statement container
            const statementElement = document.createElement("div");
            statementElement.classList.add("dialogue-step-statement");
            statementElement.innerHTML = dialogueStep.statement.fullStatement();
            statementContainer.appendChild(statementElement);

            // If there are any reply options
            const replyContainer = document.createElement("div");
            replyContainer.classList.add("dialogue-step-reply-container");
            contentBlock.appendChild(replyContainer);
            if(dialogueStep.replies.length > 0) { 
                
                let replyNumber = 1;

                dialogueStep.replies.forEach(
                    (reply) => {

                        const replyOptionContainer = document.createElement("div");
                        replyOptionContainer.classList.add("dialogue-step-reply-option-container");
                        replyContainer.appendChild(replyOptionContainer);

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
                            const replyOptionNumberElement = document.createElement("div");
                            replyOptionNumberElement.classList.add("dialogue-step-reply-number");
                            replyOptionNumberElement.innerHTML = replyNumber + ": - ";
                            replyOptionContainer.appendChild(replyOptionNumberElement);

                            const replyOptionElement = document.createElement("div");
                            replyOptionElement.classList.add("dialogue-step-reply-basic");
                            replyOptionElement.classList.add("reply-option-with-listener");
                            if(reply.endsDialogue) {
                                replyOptionElement.innerHTML = "<i class='fa-solid fa-ban'></i> " + reply.statement;
                            } else {
                                replyOptionElement.innerHTML = reply.statement;
                            }
                            replyOptionElement.addEventListener("click", this.controller.actionSelectReply.bind(this.controller, replyNumber, reply, dialogueStep), false);
                            replyOptionContainer.appendChild(replyOptionElement);
                            this.controller.dialogueReplyElements.push(replyOptionElement);
                            this.controller.dialogueReplyNumbers.push(replyOptionNumberElement);
                        }
                        replyNumber++;
                    }
                    
                );
            } else {
                // In case there are no reply options, add the "Dialogue Finished" message
                dialogueOverElement = document.createElement("div");
                dialogueOverElement.classList.add("dialogue-finished-statement");
                dialogueOverElement.innerHTML = "Dialogue Finished";
                replyContainer.appendChild(dialogueOverElement);

            }
            // Create a spacer element between different dialogue steps (this one stays, so there 
            // will always be some space between different dialogue steps).
            const spacerElement = document.createElement("div");
            spacerElement.classList.add("dialogue-step-spacer-element");
            contentBlock.appendChild(spacerElement);

            // Create a filler element that fills the "rest" of the scrollable area, to allow a proper
            // scrolling to the top (this element will be removed when rendering the next dialogue step)
            const fillerElement = document.createElement("div");
            fillerElement.setAttribute("id","temp-dialogue-filler");
            fillerElement.classList.add("dialogue-step-filler-element");
            contentBlock.appendChild(fillerElement);

            contentBlockHeight = contentBlock.getBoundingClientRect().height;
            statementContainerHeight = statementContainer.getBoundingClientRect().height;
            var replyContainerHeight = replyContainer.getBoundingClientRect().height;
            var spacerElementHeight = spacerElement.getBoundingClientRect().height;
            
            // Set the calculated height of the temporary filler element
            fillerElement.style.height = ((contentBlockHeight - statementContainerHeight - replyContainerHeight - spacerElementHeight) + "px");
            
            // Scroll to the top of scrollable element
            contentBlock.scrollTop = fillerElement.offsetTop;

        }

    }

}
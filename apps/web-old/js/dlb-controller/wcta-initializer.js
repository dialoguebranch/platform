/* @license
 *
 *                Copyright (c) 2023-2024 Fruit Tree Labs (www.fruittreelabs.com)
 *
 *
 *     This material is part of the DialogueBranch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2024 Fruit Tree Labs (www.fruittreelabs.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import { WCTAController } from './WCTAController.js';

/**
 * This anonymous function contains all the necessary "bindings" of UI to script, as well as any 
 * function calls that need to be executed after the page has finished loading.
 */
window.onload = function() {

    // Create an instance of the Controller object
    this.wctaController = new WCTAController();

    // Initialize it
    this.wctaController.initialize();

    document.getElementById("login-button").addEventListener("click", (e)=> {
        this.wctaController.actionLogin(e);
    });

    document.getElementById("toggle-debug-console").addEventListener("click", (e)=> {
        this.wctaController.actionToggleDebugConsole(e);
    });

    document.getElementById("menu-bar-logout").addEventListener("click", (e)=> {
        this.wctaController.actionLogout(e);
    });

    document.getElementById("button-refresh-dialogue-list").addEventListener("click", (e)=> {
        this.wctaController.actionRefreshDialogueBrowser(e);
    });

    document.getElementById("button-resize-dialogue-list").addEventListener("click", (e)=> {
        e.preventDefault();
        this.wctaController.actionResizeDialogueBrowser();
    });

    document.getElementById("button-refresh-variable-list").addEventListener("click", (e)=> {
        e.preventDefault();
        this.wctaController.actionRefreshVariableBrowser();
    });

    document.getElementById("button-resize-variable-list").addEventListener("click", (e)=> {
        e.preventDefault();
        this.wctaController.actionResizeVariableBrowser();
    });

    
};


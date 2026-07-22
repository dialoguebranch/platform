import './assets/css/main.css';
import config from './config.js';
import state from './state.js';
import { fetchWhoAmI, redirectToLogin } from './auth.js';
import { checkServicesHealth } from './composables/service-health.js';
import { User } from './dlb-lib/model/User.js';

import { createApp, ref } from 'vue';
import App from './App.vue';
import ServiceStatusPage from './components/pages/ServiceStatusPage.vue';
import VersionMismatchPage from './components/pages/VersionMismatchPage.vue';
import AccessDeniedPage from './components/pages/AccessDeniedPage.vue';

import { library } from '@fortawesome/fontawesome-svg-core';
import { fas } from '@fortawesome/free-solid-svg-icons';
import { far } from '@fortawesome/free-regular-svg-icons';
library.add(fas);
library.add(far);

const stateRef = ref(state);

async function bootstrap() {
    // Checked before touching auth at all — see service-health.js for why this can't be scoped
    // to only the "not logged in yet" case.
    const health = await checkServicesHealth();
    if (!health.apiUp || !health.authUp) {
        createApp(ServiceStatusPage, { apiUp: health.apiUp, authUp: health.authUp })
            .mount('#app');
        return;
    }

    // A missing serviceVersion (field absent, or /info/all's body didn't parse) is treated as
    // "unknown, don't block" rather than a mismatch — only an actual, confirmed difference between
    // two known version strings should stop the user from logging in.
    if (health.serviceVersion && health.serviceVersion !== __APP_VERSION__) {
        createApp(VersionMismatchPage, {
            clientVersion: __APP_VERSION__,
            serviceVersion: health.serviceVersion,
        }).mount('#app');
        return;
    }

    // No session yet — redirect to the BFF's login endpoint (a real top-level navigation, see
    // src/auth.js) rather than continuing to boot the app.
    const identity = await fetchWhoAmI();
    if (!identity) {
        redirectToLogin();
        return;
    }

    // 'participant' is let in too — App.vue routes participant-only users (no editor/admin) to
    // ParticipantPage.vue instead of the full authoring/testing app.
    const roles = identity.roles ?? [];
    const hasAccess = roles.includes('admin') || roles.includes('editor') || roles.includes('participant');
    if (hasAccess) {
        stateRef.value.user = new User(identity.username, roles);
    } else {
        // Authenticated with Keycloak but lacks the required application role. Rather than
        // silently ending the session and bouncing straight back to a bare Keycloak login page
        // (which has no way to explain why the user landed there again), show it here — Keycloak
        // has no notion of this app's roles, so this is the only place that can say why.
        createApp(AccessDeniedPage, { username: identity.username }).mount('#app');
        return;
    }

    const app = createApp(App);
    app.provide('config', config);
    app.provide('state', stateRef);
    app.mount('#app');
}

bootstrap();

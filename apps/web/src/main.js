import './assets/css/main.css';
import config from './config.js';
import state from './state.js';
import keycloak, { initKeycloak } from './keycloak.js';
import { User } from './dlb-lib/model/User.js';

import { createApp, ref } from 'vue';
import App from './App.vue';

import { library } from '@fortawesome/fontawesome-svg-core';
import { fas } from '@fortawesome/free-solid-svg-icons';
import { far } from '@fortawesome/free-regular-svg-icons';
library.add(fas);
library.add(far);

const stateRef = ref(state);

async function bootstrap() {
    const authenticated = await initKeycloak();
    if (authenticated) {
        const roles = keycloak.tokenParsed?.resource_access?.[config.keycloak.clientId]?.roles ?? [];
        const hasAccess = roles.includes('admin') || roles.includes('editor');
        if (hasAccess) {
            stateRef.value.user = new User(
                keycloak.tokenParsed.preferred_username,
                roles,
                keycloak.token,
                Math.round(keycloak.tokenParsed.exp - keycloak.tokenParsed.iat)
            );
        } else {
            // Authenticated with Keycloak but lacks the required application role — end the
            // Keycloak session too, otherwise silent-check-sso would keep re-authenticating the
            // same under-privileged account on every reload.
            await keycloak.logout({ redirectUri: window.location.origin });
        }
    }

    const app = createApp(App);
    app.provide('config', config);
    app.provide('state', stateRef);
    app.provide('keycloak', keycloak);
    app.mount('#app');
}

bootstrap();

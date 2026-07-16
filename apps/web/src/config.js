const config = {
    "baseUrl": import.meta.env.VITE_DLB_API_BASE_URL ?? "http://localhost:8089/dlb-web-service/v1",
    "logLevel": import.meta.env.VITE_DLB_LOG_LEVEL ?? "1",
    "keycloak": {
        "url": import.meta.env.VITE_DLB_AUTH_KEYCLOAK_URL ?? "http://localhost:8081",
        "realm": import.meta.env.VITE_DLB_AUTH_KEYCLOAK_REALM ?? "dialoguebranch",
        "clientId": import.meta.env.VITE_DLB_AUTH_KEYCLOAK_CLIENT_ID ?? "dlb-web-service"
    },
    // The project/dialogue a "participant"-only user (see ParticipantPage.vue) is automatically
    // dropped into on login — a participant can't list projects or dialogues (both are
    // editor/admin-only end-points), so there's nothing to pick from.
    "participant": {
        "projectSlug": import.meta.env.VITE_DLB_PARTICIPANT_PROJECT_SLUG ?? "default-test",
        "dialogueName": import.meta.env.VITE_DLB_PARTICIPANT_DIALOGUE_NAME ?? "menu"
    }
};

export default config;

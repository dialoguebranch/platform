const config = {
    // Relative — this app talks only to the BFF (same origin, no CORS), which proxies /api/**
    // to the actual Dialogue Branch Web Service. See src/auth.js and vite.config.js.
    "baseUrl": import.meta.env.VITE_DLB_API_BASE_URL ?? "/api/v1",
    "logLevel": import.meta.env.VITE_DLB_LOG_LEVEL ?? "1",
    // The project/dialogue a "participant"-only user (see ParticipantPage.vue) is automatically
    // dropped into on login — a participant can't list projects or dialogues (both are
    // editor/admin-only end-points), so there's nothing to pick from.
    "participant": {
        "projectSlug": import.meta.env.VITE_DLB_PARTICIPANT_PROJECT_SLUG ?? "default-test",
        "dialogueName": import.meta.env.VITE_DLB_PARTICIPANT_DIALOGUE_NAME ?? "menu"
    }
};

export default config;

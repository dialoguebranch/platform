const config = {
    "baseUrl": import.meta.env.VITE_DLB_API_BASE_URL ?? "http://localhost:8089/dlb-web-service/v1",
    "logLevel": import.meta.env.VITE_DLB_LOG_LEVEL ?? "1",
    "keycloak": {
        "url": import.meta.env.VITE_DLB_AUTH_KEYCLOAK_URL ?? "http://localhost:8081",
        "realm": import.meta.env.VITE_DLB_AUTH_KEYCLOAK_REALM ?? "dialoguebranch",
        "clientId": import.meta.env.VITE_DLB_AUTH_KEYCLOAK_CLIENT_ID ?? "dlb-web-service"
    }
};

export default config;

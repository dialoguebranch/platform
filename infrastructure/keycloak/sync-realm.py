#!/usr/bin/env python3
"""Reconciles clients from dialoguebranch-realm.json into an already-existing 'dialoguebranch'
realm: creates any client that's missing entirely, and for clients that already exist, adds any
redirectUris/webOrigins/post-logout-redirect-uris listed in the file but not yet present on the
live client. Runs as a one-shot container in infrastructure/docker/compose.yml, after Keycloak
starts, every time.

Needed because Keycloak's own --import-realm only ever fires on a genuinely fresh realm (a
brand-new server, or wiped volumes); once the realm already exists (e.g. a developer's existing
local MariaDB volume, with their own test data in it), re-mounting this same file does nothing.

It also adds client roles listed in the file but missing from the live realm, and gives each
service account (a "users" entry with a serviceAccountClientId, such as the Web Service's
dlb-web-service-evs client for the External Variable Service, issue #428) any client role the file
lists for it but it doesn't have yet. A service account is created with its client, so these
role mappings are the part --import-realm would otherwise be the only one to set up.

Deliberately additive-only, never destructive: existing entries in these list/attribute fields are
never removed, and every other field on an existing client (secret, id, everything else) is left
completely untouched — a client is fetched, has just these lists unioned in-place, and PUT back,
so its secret and UUID survive exactly as SKIP-based partial import already guaranteed. This is
what lets dialoguebranch-realm.json gain a new redirect URI for an existing client (e.g. issue #81
adding the "client" Compose profile's own origin to dlb-bff) and have it actually reach a
developer's already-provisioned local Keycloak on the next `docker compose up`, without them
needing to reset their volume or hand-edit the client in the admin console.

This deliberately does not sync client secrets from environment variables: this stack has no
production deployment, so a client's secret value doesn't matter beyond "known and stable for
local development", which is preserved for free by only ever PUTting back a client representation
this script itself just GET'd.

DLB_BFF_SESSION_TIMEOUT/DLB_BFF_SESSION_MAX_AGE are the one exception to the additive-only,
never-destructive approach above: they're each read from the environment (falling back to
dialoguebranch-realm.json's own checked-in value) and pushed as the realm's session-timeout
ceiling and the dlb-bff client's own session-timeout attributes, overwriting rather than merging,
so that changing either actually reaches a developer's already-provisioned realm instead of only
ever adding to it.

Keycloak's own depends_on condition (see compose.yml) is "service_started", the weakest one
Compose offers — it fires as soon as the container process launches, well before Keycloak has
actually finished booting, let alone running --import-realm against a genuinely fresh volume
(migrating the database and importing the whole realm export can itself take well over a minute).
wait_for_token() below is what actually absorbs that gap; its retry budget is deliberately
generous for exactly that first-run case, not just ordinary restart-speed variance.
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

KEYCLOAK_URL = os.environ.get("KEYCLOAK_INTERNAL_URL", "http://keycloak:8080")
REALM = "dialoguebranch"
REALM_EXPORT_PATH = "/realm-export.json"


def get_token() -> str:
    data = urllib.parse.urlencode(
        {
            "client_id": "admin-cli",
            "username": os.environ["KEYCLOAK_ADMIN"],
            "password": os.environ["KEYCLOAK_ADMIN_PASSWORD"],
            "grant_type": "password",
        }
    ).encode()
    req = urllib.request.Request(f"{KEYCLOAK_URL}/realms/master/protocol/openid-connect/token", data=data)
    with urllib.request.urlopen(req) as resp:
        return json.load(resp)["access_token"]


def wait_for_token(retries: int = 40, delay: float = 5) -> str:
    for attempt in range(1, retries + 1):
        try:
            return get_token()
        except (urllib.error.URLError, KeyError, ValueError):
            print(f"Waiting for Keycloak admin API... ({attempt}/{retries})")
            time.sleep(delay)
    sys.exit("Could not obtain a Keycloak admin token")


def api(method: str, path: str, token: str, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(f"{KEYCLOAK_URL}{path}", data=data, method=method)
    req.add_header("Authorization", f"Bearer {token}")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req) as resp:
        raw = resp.read()
        return json.loads(raw) if raw else None


DURATION_UNIT_SECONDS = {"s": 1, "m": 60, "h": 3600, "d": 86400}


def parse_duration_seconds(value: str) -> int:
    """Parses Spring Boot's simple duration format (e.g. "7d", "30m") into whole seconds."""
    unit = value[-1]
    if unit in DURATION_UNIT_SECONDS:
        return int(value[:-1]) * DURATION_UNIT_SECONDS[unit]
    return int(value)


# The clients whose own session-timeout attributes should track DLB_BFF_SESSION_TIMEOUT/
# DLB_BFF_SESSION_MAX_AGE rather than whatever dialoguebranch-realm.json checks in. Add an entry
# here for each new BFF instance.
BFF_CLIENT_IDS = {"dlb-bff"}

REALM_SESSION_SETTINGS = {
    "DLB_BFF_SESSION_TIMEOUT": "ssoSessionIdleTimeout",
    "DLB_BFF_SESSION_MAX_AGE": "ssoSessionMaxLifespan",
}
CLIENT_SESSION_ATTRIBUTES = {
    "DLB_BFF_SESSION_TIMEOUT": "client.session.idle.timeout",
    "DLB_BFF_SESSION_MAX_AGE": "client.session.max.lifespan",
}


def sync_realm_settings(realm: dict, token: str) -> None:
    """Pushes the realm's session-timeout ceiling, env vars if set, otherwise
    dialoguebranch-realm.json's own checked-in value. Either way this must run before
    sync_client(), Keycloak rejects a client's own session-timeout attributes if they'd exceed the
    realm's current ceiling."""
    body = {}
    for env_var, realm_key in REALM_SESSION_SETTINGS.items():
        body[realm_key] = (
            parse_duration_seconds(os.environ[env_var]) if env_var in os.environ else realm[realm_key]
        )
    api("PUT", f"/admin/realms/{REALM}", token, body)
    print(f"Synced realm settings: {list(body.keys())}")


def apply_session_env_overrides(client: dict) -> None:
    """Overrides a BFF client's checked-in session-timeout attributes from the environment, if
    set, before sync_client() reads it as the desired state."""
    if client["clientId"] not in BFF_CLIENT_IDS:
        return
    for env_var, attribute in CLIENT_SESSION_ATTRIBUTES.items():
        if env_var in os.environ:
            client.setdefault("attributes", {})[attribute] = str(parse_duration_seconds(os.environ[env_var]))


LOGOUT_URIS_ATTR = "post.logout.redirect.uris"
SESSION_ATTRIBUTE_KEYS = ("client.session.idle.timeout", "client.session.max.lifespan")


def merge_list_field(existing: dict, desired: dict, field: str) -> bool:
    """Adds any entries from desired[field] missing in existing[field] (plain JSON list fields
    like redirectUris/webOrigins). Returns whether anything changed."""
    current = existing.setdefault(field, [])
    changed = False
    for uri in desired.get(field, []):
        if uri not in current:
            current.append(uri)
            changed = True
    return changed


def merge_logout_uris(existing: dict, desired: dict) -> bool:
    """post.logout.redirect.uris isn't a JSON list — Keycloak stores it as one attribute string
    with "##" as the separator between entries. Same additive-union approach, just with that
    encoding instead of a real list."""
    existing_attrs = existing.setdefault("attributes", {})
    desired_value = desired.get("attributes", {}).get(LOGOUT_URIS_ATTR, "")
    if not desired_value:
        return False
    existing_entries = [u for u in existing_attrs.get(LOGOUT_URIS_ATTR, "").split("##") if u]
    desired_entries = [u for u in desired_value.split("##") if u]
    merged = existing_entries + [u for u in desired_entries if u not in existing_entries]
    if merged == existing_entries:
        return False
    existing_attrs[LOGOUT_URIS_ATTR] = "##".join(merged)
    return True


def merge_session_attributes(existing: dict, desired: dict) -> bool:
    """Sets the session-timeout attributes to desired's value if present and different, unlike
    the other merge_* helpers above this overwrites rather than unions: pushing an updated
    DLB_BFF_SESSION_TIMEOUT/DLB_BFF_SESSION_MAX_AGE must actually reach an already-provisioned
    client, not just get added alongside whatever it already had."""
    existing_attrs = existing.setdefault("attributes", {})
    changed = False
    for key in SESSION_ATTRIBUTE_KEYS:
        value = desired.get("attributes", {}).get(key)
        if value is not None and existing_attrs.get(key) != value:
            existing_attrs[key] = value
            changed = True
    return changed


def sync_client(desired: dict, token: str) -> None:
    client_id = desired["clientId"]
    found = api(
        "GET",
        f"/admin/realms/{REALM}/clients?clientId={urllib.parse.quote(client_id)}",
        token,
    )
    if not found:
        api("POST", f"/admin/realms/{REALM}/clients", token, desired)
        print(f"Created missing client: {client_id}")
        return

    existing = found[0]
    changed = False
    changed |= merge_list_field(existing, desired, "redirectUris")
    changed |= merge_list_field(existing, desired, "webOrigins")
    changed |= merge_logout_uris(existing, desired)
    changed |= merge_session_attributes(existing, desired)
    if changed:
        api("PUT", f"/admin/realms/{REALM}/clients/{existing['id']}", token, existing)
        print(f"Updated existing client: {client_id}")
    else:
        print(f"Already up to date: {client_id}")


def client_uuid(client_id: str, token: str) -> str:
    found = api("GET", f"/admin/realms/{REALM}/clients?clientId={urllib.parse.quote(client_id)}", token)
    return found[0]["id"]


def sync_client_roles(realm: dict, token: str) -> None:
    """Creates each client role listed in the realm export that the live client lacks."""
    for client_id, roles in realm.get("roles", {}).get("client", {}).items():
        uuid = client_uuid(client_id, token)
        existing = {role["name"] for role in api("GET", f"/admin/realms/{REALM}/clients/{uuid}/roles", token)}
        for role in roles:
            if role["name"] not in existing:
                api(
                    "POST",
                    f"/admin/realms/{REALM}/clients/{uuid}/roles",
                    token,
                    {"name": role["name"], "description": role.get("description", "")},
                )
                print(f"Created missing client role: {client_id}/{role['name']}")


def sync_service_account_roles(realm: dict, token: str) -> None:
    """Gives each service account in the realm export the client roles it lists but lacks."""
    for user in realm.get("users", []):
        owner = user.get("serviceAccountClientId")
        if not owner:
            continue
        account = api("GET", f"/admin/realms/{REALM}/clients/{client_uuid(owner, token)}/service-account-user", token)
        for client_id, role_names in user.get("clientRoles", {}).items():
            uuid = client_uuid(client_id, token)
            path = f"/admin/realms/{REALM}/users/{account['id']}/role-mappings/clients/{uuid}"
            have = {role["name"] for role in api("GET", path, token)}
            missing = [
                api("GET", f"/admin/realms/{REALM}/clients/{uuid}/roles/{urllib.parse.quote(name)}", token)
                for name in role_names
                if name not in have
            ]
            if missing:
                api("POST", path, token, missing)
                print(f"Gave {account['username']} the client roles {client_id}/{[r['name'] for r in missing]}")


def main() -> None:
    with open(REALM_EXPORT_PATH) as f:
        raw = f.read()

    # Same placeholder substitutions the Keycloak entrypoint does for a fresh --import-realm (see
    # compose.yml) — needed here too, since this script reads this file independently.
    raw = re.sub(
        r"__BFF_REDIRECT_URI__",
        os.environ.get("KEYCLOAK_BFF_REDIRECT_URI", "http://localhost:8082/login/oauth2/code/keycloak"),
        raw,
    )
    raw = re.sub(
        r"__BFF_POST_LOGOUT_REDIRECT_URI__",
        os.environ.get("KEYCLOAK_BFF_POST_LOGOUT_REDIRECT_URI", "http://localhost:5173/"),
        raw,
    )
    realm = json.loads(raw)

    token = wait_for_token()
    # Must run before the client loop: a client's own session-timeout attributes are rejected if
    # they exceed the realm's current ceiling.
    sync_realm_settings(realm, token)
    for client in realm["clients"]:
        apply_session_env_overrides(client)
        sync_client(client, token)
    sync_client_roles(realm, token)
    sync_service_account_roles(realm, token)


if __name__ == "__main__":
    main()

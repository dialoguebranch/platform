#!/usr/bin/env python3
"""Adds any client from dialoguebranch-realm.json that is missing from an already-existing
'dialoguebranch' realm. Runs as a one-shot container in infrastructure/docker/compose.yml, after
Keycloak starts, every time.

Needed because Keycloak's own --import-realm only ever fires on a genuinely fresh realm (a
brand-new server, or wiped volumes); once the realm already exists (e.g. a developer's existing
local MariaDB volume, with their own test data in it), re-mounting this same file does nothing.
Uses the Partial Import API's "SKIP" policy, so it only ever *adds* what's missing (a new client
added to dialoguebranch-realm.json later) and never touches an existing client — no wiped
projects/dialogues, no rotated secrets, no re-created client UUIDs.

This deliberately does not sync client secrets from environment variables the way the equivalent
connectedcare-nl/lizz script does: this stack has no production deployment, so a client's secret
value doesn't matter beyond "known and stable for local development", which SKIP already gives
for free (a client, once created, is never overwritten by a later run).

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


def main() -> None:
    with open(REALM_EXPORT_PATH) as f:
        raw = f.read()

    # Same placeholder substitutions the Keycloak entrypoint does for a fresh --import-realm (see
    # compose.yml) — needed here too, since a partial import reads this file independently.
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

    payload = {"ifResourceExists": "SKIP", "clients": realm["clients"]}
    result = api("POST", f"/admin/realms/{REALM}/partialImport", token, payload)
    print(f"Synced clients from dialoguebranch-realm.json: {result}")


if __name__ == "__main__":
    main()

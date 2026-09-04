# Web Services: External Variable Service

An External Variable Service is a web service that may be used by a Dialogue Branch Web Service deployment to act as an external source of information for Variable data. The Web Service itself keeps track of all Variables that are set for every individual user. For example, if a Variable is set in a dialogue using `<<set $variableName = "value">>` that value is stored. If your .dlb scripts only uses Variables that are set within the dialogue itself, the Web Service alone will handle everything.

However, if your dialogue contains a statement such as *The temperature outside is `$temperatureAtUserLocation` degrees.*, the value for `$temperatureAtUserLocation` is something that would likely need to be fetched from an external component - that is where the External Variable Service comes in.

Every time the Web Service starts executing a dialogue script, it collects a list of all the Variables used within that dialogue. The Web Service may (or may not) already have known values for these variables, but in any case, it will send a request to the External Variable Service to check whether any of the variables require updating. Your specific implementation of the External Variable Service needs to take care of these variable updates. For example, your variable service could in turn call a 3rd party weather API to retrieve the temperature at the user's location, and return this value to the Dialogue Branch Web Service.

This flow is outlined in the sequence diagram below:

![Sequence diagram for the flow of operations between a Client, the Dialogue Branch Web Service, an External Variable Service, and a 3rd Party API](/images/dlb-web-ext-var-service-sequence.png)

*Sequence diagram for the flow of operations between a Client, the Dialogue Branch Web Service, an External Variable Service, and a 3rd Party API*

The External Variable Service integration is enabled through the following `dlb.external-variable-service.*` configuration properties (see `apps/api/src/main/resources/application.yml`, overridable via `DLB_EXTERNAL_VARIABLE_SERVICE_*` environment variables):

* `dlb.external-variable-service.enabled` — set to `true` to enable the integration; `false` (the default) disables it entirely.
* `dlb.external-variable-service.url` — the base URL where the external variable service can be reached.
* `dlb.external-variable-service.api-version` — the API version to use when calling the external variable service.
* `dlb.external-variable-service.api-key` — an API key used to authenticate the Web Service's calls to the external variable service.

::: info Note
It is worthwhile to make sure that the External Variable Service answers the request for variable updates quickly, because any delay will delay the starting of dialogue execution in the Dialogue Branch Web Service - which will negatively impact your end-user's experience. Apply caching, and make use of the provided `updatedTime` parameter that is passed along with each Variable, to make quick judgements whether a variable needs to be updated at all.
:::

## Request identity

Every call the Web Service makes to the External Variable Service (`retrieve-updates`, `notify-updated`, `notify-cleared`) carries the same query parameters identifying whose variables, in which project, the request concerns:

| Parameter | Description |
|---|---|
| `subject` | The user's stable OIDC `sub` claim — the identifier your service should key on. |
| `issuer` | The full OIDC issuer URL (`iss`) the user authenticates through. One Web Service instance may trust several Keycloak realms, so `subject` is only unique *within* an issuer; the pair `(issuer, subject)` is the stable user identity. |
| `projectSlug` | The slug of the Dialogue Branch project the variables belong to. Variables are project-scoped — the same variable name in two projects is two independent values. |
| `timeZone` | The user's current IANA time zone (e.g. `Europe/Lisbon`). |

::: warning Breaking change
The earlier single `userId` parameter (a bare username) has been replaced by `subject` + `issuer` + `projectSlug`. Update your External Variable Service implementation accordingly; there is no API-version bump.
:::

See also [Working with Variables](/web-services/api-service#working-with-variables) on the API Service page, for how Variables are set and retrieved directly through the Web Service's own `/variables/*` end-points.

## Reporting supported variables

A dialogue author has no built-in way to know which `$variable` names a configured External Variable Service actually understands. Any variable reference is syntactically valid, so a dialogue can reference `$stepsToday` expecting the EVS to fill it in while the EVS only knows `$numberOfStepsToday` — a naming mismatch that is otherwise silent, since a variable with no value is never treated as an error by the Web Service.

To make this discoverable, your External Variable Service may implement one additional, optional end-point:

`GET /v{version}/variables/supported?projectSlug=<slug>`

* Same static API-key authentication as `retrieve-updates`/`notify-updated`/`notify-cleared`.
* `projectSlug` is **required**, not optional. An External Variable Service deployment is typically multi-tenant (one deployment, many projects), and which variables it can actually compute may differ per project — a project without a given data integration enabled should not advertise a variable it can never fill for that project's users. A single-project EVS can simply ignore the parameter and return the same list every time; requiring it costs that implementation nothing.
* Response: a bare JSON array of objects, each with a required `name` and an optional `description`, e.g.:

  ```json
  [
    { "name": "stepsToday", "description": "Steps taken so far today" },
    { "name": "partOfDay" }
  ]
  ```

* This is a **static, EVS-wide-per-project** capability list, not a per-user one: it answers "can this EVS compute this variable for this project at all," not "does this specific user have a value right now" (the latter is a legitimate `null` from `retrieve-updates`, a different and unrelated kind of gap).

The Web Service exposes this to clients like Dialogue Branch Studio via `GET /variables/list-supported?projectSlug=<slug>` (see [Working with Variables](/web-services/api-service#working-with-variables)), proxying each call to the EVS **live, with no caching**. This call is not on the dialogue-execution hot path the way `retrieve-updates` is, so there is no latency pressure to justify storing a second, potentially stale copy of the EVS's own answer — if your EVS is unreachable, the Web Service reports that directly rather than returning a cached or empty list that could be mistaken for "supports nothing."

An External Variable Service that does not implement `/variables/supported` is not an error: the Web Service treats a missing or unimplemented end-point as "capability unknown." This is purely a discovery mechanism — the Web Service does not validate dialogue content against it, warn about unsupported variables, or otherwise change execution behavior based on it.

::: info Note
If you found errors or have questions about this page, please consider reporting an issue at https://github.com/dialoguebranch/platform or sending an email to info@dialoguebranch.com.
:::

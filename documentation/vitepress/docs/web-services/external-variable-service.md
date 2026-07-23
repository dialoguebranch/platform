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

See also [Working with Variables](/web-services/api-service#working-with-variables) on the API Service page, for how Variables are set and retrieved directly through the Web Service's own `/variables/*` end-points.

::: info Note
If you found errors or have questions about this page, please consider reporting an issue at https://github.com/dialoguebranch/platform or sending an email to info@dialoguebranch.com.
:::

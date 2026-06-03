# Dialogue Branch External Variable Service

## Quickstart
Run a standalone version of this service by pulling the latest official Docker image:

```
docker run -d -p 8090:8090 --name dlb-evs dialoguebranch/dlb-external-var-service
```

## What is this service?
If you want the Dialogue Branch Web Service to synchronize its variable data with an "external" service, you can configure it to connect to an "External Variable Service". If this is setup, the following will happen:

  * Before starting a dialogue, the Web Service will ask the configured External Service: "Hey, I'm about to start a dialogue with variables `$A`, `$B`, and `$C` - do you have any up-to-date values for these?" (i.e. `/variables/retrieve-updates`).
  * Whenever a variable is updated in the Web Service (either through dialogue execution, or through a direct request to the Web Service to update a value), it will notify the External Service of this change: "Hey, variable `$A` just got updated to value `someValue`" (i.e. `/variables/notify-updated`).
  * In the unlikely event that a user's Variable Store is completely cleared out in the Web Service, it will notify the External Service of this as well: "Hey, all data for user `$userName` has just been wiped!" (i.e. `\variables\notify-cleared`).

This is the way that you can connect the Dialogue Branch Web Service to your existing back-end service.

This specific module `dlb-external-var-service` is a Dummy implementation that provides the bare minimum functionality of what an "External Variable Service" must provide. It is used for testing the connection between DLB Web Service and this external service. Specifically, this is what it does:

  * For every variable that you include in the request list to `/variables/retrieve-updates` there is a 50% chance that it will be returned in the response list with the same value as provided in the request, and the lastUpdated time set to the current UTC time in epoch seconds.
  * If an update is requested for a variable with name `$currentDate` and/or `$currentTime`, those two variables will be updated and returned to reflect the current date and time in the user's provided time zone.
  * Calls to `/notify-updated` and `/notify-cleared` will be checked for correctness (e.g. Authorization header must be correct), but otherwise don't result in anything.

If you have an existing back-end service, and you want it to take up the role of "Dialogue Branch External Variable Service", here is what you do:

  * Implement the set of end-points somewhere in your service's API:
    * `/variables/retrieve-updates`
    * `/variables/notify-updated`
    * `/variables/notify-cleared`
  * Verify the access token that is sent along with every request to these end-points (`Authorization: Bearer <someToken>`). At the moment, `someToken` is a pre-configured API Key, defined in the Web Service as `externalVariableServiceAPIKey`, but additional security schemes may be implemented at a later point in time.

## Building and Running
Typically, this Dummy service is deployed alongside the DLB Web Service, Keycloak, and other services. However, if you want to run this as a standalone service, follow these steps:

### Building Docker Image

Open a Terminal in the current folder () `/dlb-external-var-service/`):

Build the Docker Image using:

```dockerfile
docker build -t dialoguebranch/dlb-external-var-service .
```

Then, run the docker image using:

```dockerfile
docker run -d -p 8090:8090 --name dlb-evs dialoguebranch/dlb-external-var-service
```

### Test

After running the docker image, open a web browser and navigate to the following page to view the service's Swagger pages:

```
http://localhost:8090/dlb-external-var-service/
```

### Publishing

Set an appropriate tag for the image using the following

```dockerfile
docker tag dialoguebranch/dlb-external-var-service:latest dialoguebranch/dlb-external-var-service:1.3.0
```

Make sure you are logged in to Docker Hub

```dockerfile
docker login
```

Then, push the image using the following:

```dockerfile
docker push dialoguebranch/dlb-external-var-service:1.3.0
```

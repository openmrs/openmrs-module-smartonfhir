# Connecting an app to OpenMRS SMART on FHIR

Step-by-step instructions for making your app read OpenMRS clinical data. You write no OpenMRS code
and deploy no OpenMRS module: you register a client, launch it, and call FHIR.

The steps follow the order in
[SMART App Launch 2.2.0](https://hl7.org/fhir/smart-app-launch/app-launch.html), so each one here maps
onto a section of the specification.

- [Which integration do you actually need](#which-integration-do-you-actually-need)
- [Step 1 — Register your app](#step-1--register-your-app)
- [Step 2a — Standalone launch](#step-2a--standalone-launch)
- [Step 2b — EHR launch](#step-2b--ehr-launch)
- [Step 3 — Retrieve the discovery document](#step-3--retrieve-the-discovery-document)
- [Step 4 — Obtain an authorization code](#step-4--obtain-an-authorization-code)
- [Step 5 — Obtain an access token](#step-5--obtain-an-access-token)
- [Step 6 — Access the FHIR API](#step-6--access-the-fhir-api)
- [Step 7 — Refresh the access token](#step-7--refresh-the-access-token)
- [Step 8 — End the session](#step-8--end-the-session)
- [Adding a scope the realm does not define](#adding-a-scope-the-realm-does-not-define)
- [Symptoms and causes](#symptoms-and-causes)

## Which integration do you actually need

Answer this before writing anything, because for a large share of OpenMRS work the answer is **not
SMART**.

| Your app | What to use |
|---|---|
| **An O3 frontend module (ESM) that ships as part of the EMR** — a dashboard, a chart widget, a workspace | **Not SMART.** Call the FHIR API directly with `openmrsFetch`. The clinician is already signed in and the session cookie authenticates you. Adding SMART gains nothing: it authenticates a user who is already authenticated. |
| **A separately deployed app**, yours or a third party's, that should work against any SMART-capable EHR | **SMART.** [Standalone launch](#step-2a--standalone-launch) if the user starts it from its own URL. |
| **A separately deployed app the clinician should open from a patient's chart** | **SMART EHR launch.** Works end to end, registered apps only, and reachable from the interface: **Launch an app** in the patient banner's Actions menu — see [step 2b](#step-2b--ehr-launch). |
| **An unattended service** — nightly export, population analytics, a monitoring job with no user | **Not supported yet.** SMART Backend Services (`client_credentials` with an asymmetric client assertion) is not implemented. See [CONFORMANCE.md](CONFORMANCE.md). |

An O3 ESM reading FHIR with the user's session looks like this, and needs nothing from this module:

```ts
import { openmrsFetch } from '@openmrs/esm-framework';

const { data } = await openmrsFetch(`/ws/fhir2/R4/Observation?patient=${patientUuid}&_count=50`);
```

The rest of this document is for the SMART cases.

## Step 1 — Register your app

*Spec: [Register App with EHR](https://hl7.org/fhir/smart-app-launch/app-launch.html#register-app-with-ehr).*
A one-time step, done by whoever administers the Keycloak realm. There is no dynamic registration.

### Using the Keycloak admin console

1. Sign in to Keycloak (`http://localhost:8180` in the development stack; `admin`/`admin`).
2. Choose the **openmrs** realm in the realm selector, top left. *Not* `master` — that realm
   administers Keycloak itself and has no OpenMRS users in it.
3. **Clients → Create client**.
4. *General settings*: **Client type** `OpenID Connect`, **Client ID** your app's identifier
   (for example `risk-dashboard`). This is the `client_id` you will send in step 4. **Next**.
5. *Capability config*:
   - **Client authentication**: **Off** for a browser or mobile app — it cannot keep a secret, and
     PKCE protects the authorization code instead. **On** for a server-side app, which then gets a
     secret under *Credentials*.
   - **Authentication flow**: check **Standard flow** only. Leave **Direct access grants**
     unchecked; the password grant is not a SMART launch.
   - **Next**.
6. *Login settings*:
   - **Valid redirect URIs**: your exact callback, for example
     `https://risk.example.org/callback`. Keep it as narrow as you can — this is what stops an
     authorization code being delivered somewhere else. Avoid a bare `*`.
   - **Valid post logout redirect URIs**: where the browser should land after logout (step 8).
   - **Web origins**: only if your app calls the token endpoint from browser JavaScript.
   - **Save**.
7. Open the **Advanced** tab → *Advanced settings* → set **Proof Key for Code Exchange Code Challenge
   Method** to **S256**. SMART 2.x requires S256 and forbids `plain`; leaving this unset allows a
   client to skip PKCE entirely.
8. Open the **Client scopes** tab and confirm the **Default** list contains **`profile`** and
   **`fhir-audience`**. Every new client inherits both. Do not remove them — see the warning below.
9. On the same tab, **Add optional scope** for the SMART scopes your app will request:
   `launch/patient`, `launch/encounter`, `fhirUser`, `patient/Patient.rs`,
   `patient/Observation.rs`, `patient/Condition.rs`, `patient/Encounter.rs`, `offline_access`.
   Optional scopes are granted only when your app asks for them.

> **Do not remove `profile` or `fhir-audience`.** `fhir-audience` stamps the OpenMRS FHIR server into
> your token's `aud`, and the resource server refuses any token that does not name it.
> `profile` carries the `preferred_username` claim, which is how the resource server works out which
> OpenMRS user you are acting as. Without either one your launch **succeeds** and then every FHIR call
> answers `401`, with nothing in the response to connect it to a scope. Both were once attached to a
> single client, and the second app registered against the realm hit exactly this.

### The same thing from the command line

If you administer Keycloak with `kcadm.sh`, this is equivalent to steps 3–7:

```bash
kcadm.sh config credentials --server http://localhost:8180 \
  --realm master --user admin --password admin

kcadm.sh create clients -r openmrs \
  -s clientId=risk-dashboard \
  -s name="Patient Risk Dashboard" \
  -s enabled=true \
  -s publicClient=true \
  -s standardFlowEnabled=true \
  -s directAccessGrantsEnabled=false \
  -s 'redirectUris=["https://risk.example.org/callback"]' \
  -s 'attributes."post.logout.redirect.uris"=https://risk.example.org/' \
  -s 'attributes."pkce.code.challenge.method"=S256'
```

Confirm what it inherited, which is the step people skip:

```bash
CLIENT=$(kcadm.sh get clients -r openmrs -q clientId=risk-dashboard \
  --fields id --format csv --noquotes)
kcadm.sh get "clients/$CLIENT/default-client-scopes" -r openmrs --fields name
# expect profile and fhir-audience
```

> In the development stack, Keycloak is recreated whenever its container is, and the realm is imported
> fresh each time, so a client you register by hand disappears on the next `docker compose up
> --force-recreate keycloak`. Add it to the realm instead —
> [openmrs-distro-smartonfhir](https://github.com/mherman22/openmrs-distro-smartonfhir)
> `keycloak/realm/openmrs-realm.json` — so it is imported every time. That file holds `${...}`
> placeholders which Keycloak substitutes from its own environment during `--import-realm`; there is no
> rendering step. In a real deployment you register once and this does not arise.

## Step 2a — Standalone launch

*Spec: [Launch App: Standalone Launch](https://hl7.org/fhir/smart-app-launch/app-launch.html#launch-app-standalone-launch).*

The user opens your app directly — a bookmark, an icon, your own login page. There is no launch
notification and no `launch` parameter. Your app simply proceeds to step 3, and asks for the patient
context it needs by requesting `launch/patient` in step 4, which is what causes the clinician to be
shown a patient-selection screen.

## Step 2b — EHR launch

*Spec: [Launch App: EHR Launch](https://hl7.org/fhir/smart-app-launch/app-launch.html#launch-app-ehr-launch).*

A clinician is working in OpenMRS, looking at a patient, and opens your app for them. This works end to
end, from the interface: **Launch an app** in the patient banner's Actions menu lists what the server has
registered and starts a launch for the patient in the chart. The menu is hidden when nothing is
registered.

**Register your app in the registry as well as at Keycloak.** Keycloak knows your `client_id` and
redirect URI; OpenMRS needs to know your *launch* URL. Add an entry to
`{application data directory}/config/smart-apps.json`:

```json
{
  "apps": [
    {
      "id": "risk-dashboard",
      "name": "Patient Risk Dashboard",
      "description": "Shown in the chart when a clinician chooses an app",
      "clientId": "risk-dashboard",
      "launchUrl": "https://risk.example.org/launch",
      "launchContext": "patient"
    }
  ]
}
```

`id` is how a launch names your app, `launchUrl` is where the browser is sent. An app that is not in
this file cannot be launched: the launch address is looked up here rather than supplied by whoever
starts the launch, because it used to be a request parameter and that made the servlet an open
redirector — a single-use launch handle delivered to any host named in the URL.

The launch is then started by sending the clinician's browser to:

```
{openmrs}/ms/smartEhrLaunchServlet?appId=risk-dashboard&patientId={patient uuid}
```

which redirects to your launch URL with the two parameters the specification requires:

```
https://risk.example.org/launch?iss={openmrs}/ws/fhir2/R4&launch={opaque handle}
```

Your app reads `iss`, fetches discovery from it (step 3), and in step 4 includes **`launch={handle}`**
and the **`launch`** scope — not `launch/patient`, because the EHR has already chosen the patient.
Everything from step 3 onward is identical to a standalone launch, and the token response carries the
patient the clinician was looking at.

No password is asked for: the clinician's OpenMRS session is what authenticates the launch.

**Building your own launcher.** The Actions-menu entry is a frontend module
([openmrs-esm-smart-app-launch-app](https://github.com/mherman22/openmrs-esm-smart-app-launch-app)) rather
than part of this one, so you can replace it. `GET {openmrs}/ms/smartApps` lists the registered apps —
`id`, `name`, `description`, `launchContext`, and deliberately not launch URLs or client ids, which a
chart screen has no use for. If you are writing an O3 frontend module to do this, you are building the
*launcher* rather than a SMART app; see the first row of
[the table above](#which-integration-do-you-actually-need).

**A worked example.** [openmrs-smart-vitals-reviews-app](https://github.com/mherman22/openmrs-smart-vitals-reviews-app)
is a complete SMART app that implements both launches against this server, in under a thousand lines of
plain JavaScript with one runtime dependency. Steps 3 to 8 below are all visible in its `src/launch.js` and `src/app.js`,
and it is the shortest route to seeing a launch work before you write your own.

## Step 3 — Retrieve the discovery document

*Spec: [Retrieve .well-known/smart-configuration](https://hl7.org/fhir/smart-app-launch/app-launch.html#retrieve-well-knownsmart-configuration).*

```bash
curl -H 'Accept: application/json' \
  http://localhost/openmrs/ws/fhir2/R4/.well-known/smart-configuration
```

Read the endpoints from here rather than hardcoding them; a deployment can put Keycloak anywhere.
You need `authorization_endpoint`, `token_endpoint` and `end_session_endpoint`. `capabilities` tells
you which launch flows the server actually claims — see [CONFORMANCE.md](CONFORMANCE.md).

## Step 4 — Obtain an authorization code

*Spec: [Obtain authorization code](https://hl7.org/fhir/smart-app-launch/app-launch.html#obtain-authorization-code).*

Send the browser to the `authorization_endpoint`:

```
GET {authorization_endpoint}
  ?response_type=code
  &client_id=risk-dashboard
  &redirect_uri=https%3A%2F%2Frisk.example.org%2Fcallback
  &scope=openid+fhirUser+launch%2Fpatient+patient%2FObservation.rs+patient%2FCondition.rs
  &state={unpredictable value, at least 122 bits}
  &aud=http%3A%2F%2Flocalhost%2Fopenmrs%2Fws%2Ffhir2%2FR4
  &code_challenge={S256 of your verifier}
  &code_challenge_method=S256
```

| Parameter | Notes |
|---|---|
| `aud` | **Mandatory here.** It must name the FHIR server you intend to call. A launch that omits it, or names a different server, is refused *before* the clinician sees a login form — so a token minted for one FHIR server can never be presented to another. Trailing-slash differences are tolerated; nothing else is. |
| `scope` | Ask for `launch/patient` if your app is about one patient: that is what causes a patient-selection screen to be shown and a patient to be returned. Request the least you need. |
| `state` | Validate it when the browser returns. It is your protection against CSRF and session fixation. |
| `code_challenge_method` | `S256` only. |
| `launch` | EHR launch only, echoing the handle from step 2b. Omit for standalone. |

The clinician signs in with their ordinary OpenMRS username and password — Keycloak reads users from
the OpenMRS database, so there is no second directory and no separate password. Then the browser
returns to your `redirect_uri` with `code` and `state`.

## Step 5 — Obtain an access token

*Spec: [Obtain access token](https://hl7.org/fhir/smart-app-launch/app-launch.html#obtain-access-token).*

```bash
curl -X POST {token_endpoint} \
  -d grant_type=authorization_code \
  -d code={code} \
  -d redirect_uri=https://risk.example.org/callback \
  -d code_verifier={your verifier} \
  -d client_id=risk-dashboard        # public clients only; confidential clients authenticate instead
```

```json
{
  "access_token": "eyJ…",
  "token_type": "Bearer",
  "expires_in": 300,
  "scope": "openid fhirUser launch/patient patient/Observation.rs patient/Condition.rs profile",
  "patient": "b925b60f-8751-4b2f-b38a-a35002961be4",
  "id_token": "eyJ…"
}
```

**Launch context arrives in the token response, not inside the JWT.** `patient` is a sibling of
`access_token`, exactly as the specification defines. Decoding the access token and looking for a
`patient` claim finds nothing, and that is correct.

Keep the `id_token`: you need it to log out cleanly in step 8.

## Step 6 — Access the FHIR API

*Spec: [Access FHIR API](https://hl7.org/fhir/smart-app-launch/app-launch.html#access-fhir-api).*

```bash
curl -H "Authorization: Bearer {access_token}" \
  "http://localhost/openmrs/ws/fhir2/R4/Observation?patient={patient}&_count=50"
```

Every resource the FHIR2 module supports is available. The token is verified per request against the
authorization server's published signing keys; it is not a login and leaves no session behind.

> **Scopes are not enforced yet.** They are parsed, granted and returned, but the resource server does
> not restrict requests by them: a token granted only `patient/Observation.rs` can in practice read
> other resource types, and the **user's** OpenMRS privileges are the real boundary. `permission-v2`
> is deliberately absent from the advertised capabilities for this reason. Treat scopes as your
> declaration of intent and do not rely on them as a security boundary.

## Step 7 — Refresh the access token

*Spec: [Refresh access token](https://hl7.org/fhir/smart-app-launch/app-launch.html#refresh-access-token).*

Access tokens last 300 seconds. Request `offline_access` in step 4 to be issued a refresh token, then:

```bash
curl -X POST {token_endpoint} \
  -d grant_type=refresh_token \
  -d refresh_token={refresh token} \
  -d client_id=risk-dashboard
```

`online_access` is not implemented; only `offline_access` is available.

## Step 8 — End the session

Ending your own session is not enough. The authorization server keeps its own, and the next launch in
that browser is granted **silently, as the previous clinician** — on a shared clinical workstation,
somebody else's identity. Send the browser to the `end_session_endpoint`:

```
GET {end_session_endpoint}
  ?id_token_hint={the id_token from step 5}
  &post_logout_redirect_uri=https%3A%2F%2Frisk.example.org%2F
```

`id_token_hint` is what lets the authorization server end the session without stopping to ask the
clinician "do you want to log out?". The `post_logout_redirect_uri` must be registered on your client
(step 1, login settings).

## Adding a scope the realm does not define

SMART scopes are Keycloak client scopes. Requesting one that does not exist is rejected outright, and
your app is redirected back with:

```
?error=invalid_scope&error_description=Invalid+scopes:+openid+patient/MedicationRequest.rs
```

The realm defines `Patient`, `Observation`, `Condition` and `Encounter`. To add another, in the admin
console: **Client scopes → Create client scope**, name it exactly as the SMART scope
(`patient/MedicationRequest.rs`), **Protocol** `openid-connect`, **Type** `Optional`, and turn
**Include in token scope** on. Then either add it to your client's optional scopes, or — better — to
the realm's default optional scopes, so every client may request it.

Prefer adding it to the realm rather than to one client: one client working is not the same as the
realm working, which is the mistake that produced the warning in step 1.

## Symptoms and causes

Every row here is a failure seen while building and verifying this.

| Symptom | Cause |
|---|---|
| Refused before the login form appears | `aud` missing, or not naming this FHIR server |
| `error=invalid_scope` at your redirect URI | A requested scope is not defined in the realm |
| Launch succeeds, every FHIR call `401` | Your client lost the `fhir-audience` or `profile` default scope |
| `401` and the server log says `JWT missing required audience` | As above: `fhir-audience` |
| `401` and the log says `carries no 'preferred_username' claim` | As above: `profile` |
| No `patient` in the token response | You did not request `launch/patient` |
| No `patient` claim inside the access token | Correct — launch context is in the token response |
| The next launch never asks for a password | You ended your session but not the authorization server's; use step 8 |
| Redirected to a location-picker screen mid-launch | The OpenMRS session has no login location set |
| `401` with an HTML body from a REST endpoint | A stale session cookie for a session that no longer exists; clear cookies |
| `Client not allowed` from the introspection endpoint | Introspection requires a confidential client; see [CONFORMANCE.md](CONFORMANCE.md) |

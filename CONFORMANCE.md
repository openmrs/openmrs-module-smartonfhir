# Conformance against SMART App Launch 2.2.0

Measured against [SMART App Launch 2.2.0 (STU 2.2)](https://hl7.org/fhir/smart-app-launch/), section
by section. Every "yes" below was exercised against a running server; the failures are recorded with
what the server actually answered, not with what it ought to answer.

The advertised `capabilities` array is the machine-readable version of this document. Where the two
could disagree, the rule is that a capability is claimed only after the flow behind it has been walked
end to end — the discovery document is a contract, and an app that trusts a capability we do not
implement fails in a way that looks like the app's fault.

## Apps: launch and authorization

| Spec requirement | Status | Notes |
|---|---|---|
| Register app (redirect URIs, launch URL) | **Manual** | No dynamic client registration. Redirect URIs at Keycloak; launch URLs in the module's `config/smart-apps.json`. |
| Standalone launch | **Yes** | Walked end to end in a browser, including patient selection. |
| EHR launch | **Yes** | Walked in a browser from a patient chart: "Launch an app" in the banner Actions menu, the app is notified with `iss` and `launch`, the handle is redeemed against the clinician's OpenMRS session with no password prompt, and the token response carries the patient. Apps are registered in `config/smart-apps.json`, so an unregistered app cannot be launched and the launch address cannot be supplied by the caller. |
| `.well-known/smart-configuration` | **Yes** | All SMART 2.x required fields present. |
| Authorization code flow | **Yes** | |
| PKCE, `S256` required, `plain` refused | **Yes** | `code_challenge_methods_supported` is `["S256"]` only. |
| `aud` required, validated | **Yes** | Enforced by a Keycloak authenticator before authentication. A launch omitting `aud`, or naming another server, is refused. Fails closed. |
| `state` round-tripped | **Yes** | Keycloak. Validating it is the app's responsibility. |
| Access token response carries launch context | **Yes** | `patient` is returned alongside `access_token`, per spec, not as a JWT claim. |
| `openid` + `fhirUser` produce an `id_token` | **Yes** | |
| Bearer token accepted on FHIR requests | **Yes** | Verified per request against the authorization server's published keys. Asymmetric algorithms only. |
| Refresh via `offline_access` | **Yes** | Tokens last 300s; refresh returns a new access token. |
| Refresh via `online_access` | **No** | Scope not defined. |
| `authorization_details` (experimental, multi-server) | **No** | Not implemented. |
| Public clients | **Yes** | |
| Confidential clients, symmetric (client secret) | **Yes** | |
| Confidential clients, asymmetric (`private_key_jwt`) | **Advertised, untested** | `token_endpoint_auth_methods_supported` includes `private_key_jwt`. Keycloak supports it; this project has never exercised it. Treat as unverified. |
| CORS on discovery and token endpoints | **Yes** | Measured with a preflight from a third-party origin. The FHIR API answers `Access-Control-Allow-Origin: *` and permits the `Authorization` header; the discovery document answers `*`; Keycloak's token endpoint echoes the requesting origin and permits `Authorization`. A browser-only application on another origin can therefore complete the handshake and read the record. |

## Scopes and launch context

| Spec requirement | Status | Notes |
|---|---|---|
| `launch/patient` | **Yes** | Produces the patient-selection screen and returns `patient`. |
| `launch/encounter` | **EHR half only** | An EHR launch naming a visit returns it as `encounter`. A standalone launch asking for it is refused with `501`, because choosing a visit needs a screen that does not exist — it no longer redirects to a deleted page. `context-ehr-encounter` is still not advertised: the EHR half works but no deployment walks it, and a capability nothing walks is one nobody notices breaking. |
| `launch` (EHR context) | **Yes** | The scope carries both context mappers. It carried none for a while, so an EHR launch completed and returned no patient at all. |
| v2 granular scope **syntax** (`patient/Observation.rs`) | **Yes** | Parsed, granted, returned in `scope`. |
| v2 granular scope **enforcement** | **No** | The resource server does not restrict requests by scope; the user's OpenMRS privileges are the boundary. `permission-v2` is therefore not advertised. |
| `patient/*.rs`, `user/*.rs` wildcards | **No** | Expanding a wildcard is the authorization server's job and Keycloak does not do it: a scope must exist as a client scope to be requestable, and both forms answer `invalid_scope`. They were advertised in `scopes_supported` until that was measured; the advertisement has been removed rather than the truth adjusted. |
| `offline_access` | **Yes** | |
| `fhirUser` claim | **Yes** | In the `id_token`, as `Practitioner/{uuid}` resolved from the user's provider record. It was granted but absent for a while: the `fhirUser` client scope had no protocol mapper on it, so the scope was returned and the claim was not. |

## Backend Services

**Not implemented.** None of it: no `client_credentials` grant, no `system/` scopes, no
`client-confidential-asymmetric` registration, no JWKS-based client assertion.

This is the profile for unattended clients — nightly exports, population analytics, a lab monitoring
service, bulk data. It shares nothing with the launch flows above except the discovery document, so it
is separable work rather than an extension of what exists. `system/` scopes in particular are
meaningless until scope enforcement exists, since an unattended client has no user whose privileges
could stand in for them.

## Token introspection

| Spec requirement | Status | Notes |
|---|---|---|
| `introspection_endpoint` advertised | **Only when configured** | Stated, never derived. It used to be derived from the issuer, which advertised an endpoint every app could find and no app we ship could use. A deployment that has registered a confidential client for introspection sets `introspection-endpoint` and gets it advertised again. |
| Usable by the app we ship | **No** | A public client gets `403 {"error":"invalid_request","error_description":"Client not allowed."}`. Keycloak requires a confidential client. |
| Authenticating to it with a SMART bearer token | **No** | `401 invalid_client`. The spec says any client authorized to introspect SHALL be able to authenticate this way; Keycloak does not support it. |
| Required fields (`active`, `scope`, `client_id`, `exp`) | **Untested** | Blocked on the above. Keycloak returns these for a confidential client. |
| Launch context (`patient`) in the introspection response | **Unknown** | The spec requires it when the token response carried it. Our context mapper writes to the token response; whether Keycloak mirrors it into introspection has not been checked. |

This was an open item and is now closed the honest way: rather than make introspection work for a public
client, which Keycloak will not do, the endpoint is no longer advertised unless a deployment configures
one. The rows above still record what happens if you try it.

## Brands (User-access Brands and Endpoints)

**Not implemented.** No `Brand` or `Endpoint` resources are published. Relevant only for patient-facing
app directories.

## Measured by Inferno

Run against **Inferno's SMART App Launch STU2.2** suite, Standalone Launch group, on 2026-08-21 --
the first time anything outside this project has read it:

**24 pass, 2 fail.** Both failures are `standalone_auth_tls` and `standalone_token_tls`, "Server did not
support any allowed TLS versions", which is a local deployment served over plain HTTP rather than a
conformance defect. Everything else passed: discovery and its capabilities, CORS on `.well-known`,
`metadata`, the token endpoint and the userinfo path, the redirect, code receipt, token exchange, token
response body and headers, OpenID discovery, JWKS retrieval, id_token header and payload validation, and
refresh.

It found one real defect, which is fixed. A standalone launch granted the `fhirUser` scope and then
issued an id_token **without the claim** -- `ID token does not contain fhirUser claim`. The resolver
lived in the EHR-launch servlet, so the standalone path never ran it; the two paths built different claim
sets. Nothing here caught it because the application this project ships does not read `fhirUser`. After
the fix the suite reports 24 pass and the two TLS failures, with `smart_openid_fhir_user_claim` and
`smart_cors_openid_fhir_user_claim` both passing.

Not yet run: the **EHR Launch**, **Backend Services** and **Token Introspection** groups. Backend
Services is not implemented at all, and introspection is only advertised where a deployment configures
it, so both are expected to fail; the EHR Launch group is the one worth running next.

## Where this document is enforced

- `SmartConfigServlet.CAPABILITIES` — the claimed capability list, with a comment per claim recording
  what was walked to earn it and why each absent capability is absent.
- This module's own tests — 99, including that the discovery document is refused rather than served
  half-configured.
- `ProviderContractTest` in [openmrs-contrib-keycloak-smart-auth](https://github.com/mherman22/openmrs-contrib-keycloak-smart-auth)
  — that every authenticator is registered for Keycloak to discover, that provider ids fit the column
  Keycloak stores them in, and that the audience validator cannot be configured as `ALTERNATIVE`, since
  an audience check a sibling execution can satisfy instead is not a check.
- `e2e/specs/` in [openmrs-esm-smart-app-launch-app](https://github.com/mherman22/openmrs-esm-smart-app-launch-app)
  — `capture-walkthrough` drives the whole EHR launch in a browser and asserts at every step,
  `standalone-launch` does the same for the other flow, and `chart-action` covers the launcher itself.
- [openmrs-smart-vitals-reviews-app](https://github.com/mherman22/openmrs-smart-vitals-reviews-app) —
  an independent SMART app that implements both launches against this server, so the launch is exercised
  by a client that shares no code with it.

There is no `verify-env.sh` any more. It and the rest of the distribution's shell plumbing were removed;
the walkthrough and its recorded redirect chain in
[openmrs-distro-smartonfhir](https://github.com/mherman22/openmrs-distro-smartonfhir) `docs/` replace it,
and they are regenerated by the Playwright capture rather than maintained by hand.

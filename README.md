# CRM247 Android Tracker SDK (Kotlin)

Initial SDK scaffold for mobile event tracking compatible with the CRM247 backend visitor tracking endpoints.

## Current status

This is `v0.1.0` starter SDK with:

- `init(context, config)`
- `identify(email, properties)`
- `screen(screenName, properties)`
- `track(eventType, metadata)`
- `flush()`
- `reset()`
- `shutdown()`

It posts to:

- `POST /api/v1/crm/tracking/visitor`
- `POST /api/v1/crm/tracking/events/batch`

and automatically sends required metadata for dedupe:

- `metadata.page_visit_id`
- `metadata.event_seq`

## Module layout

- `tracker/` Android library module
- Package: `ai.crm247.tracker`

## Quick start (Kotlin)

```kotlin
val trackerConfig = Crm247Config(
    domainId = "your-domain-id",
    baseUrl = "https://api.auray.net",
    consentRequired = false,
    debug = true,
)

Crm247Tracker.init(applicationContext, trackerConfig)

Crm247Tracker.screen("HomeScreen")
Crm247Tracker.track("click", mapOf("element_text" to "Start Trial"))
Crm247Tracker.identify("user@company.com", mapOf("plan" to "pro"))
```

## Quick start (Java)

```java
Crm247Config config = new Crm247Config(
    "your-domain-id",
    "https://api.auray.net",
    null,
    "crm247.ai",
    43200,
    10,
    5000L,
    500,
    10000,
    10000,
    false,
    null,
    true
);

Crm247Tracker.INSTANCE.init(getApplicationContext(), config);
Crm247Tracker.INSTANCE.screen("HomeScreen", java.util.Collections.emptyMap());
Crm247Tracker.INSTANCE.track("click", java.util.Collections.singletonMap("element_text", "Start Trial"));
Crm247Tracker.INSTANCE.identify("user@company.com", java.util.Collections.emptyMap());
```

## Recommended usage patterns

1. Call `init` once in `Application.onCreate()`.
2. Call `screen()` from each screen entry point (Activity/Fragment/Compose route).
3. Call `identify()` after login or when user email is known.
4. Call `reset()` on logout.
5. Call `shutdown()` from app background lifecycle hook if you want immediate flush.

## Event mapping used by SDK

- `screen()` -> `page_view`
- `track("click", ...)` -> `click`
- `track("form_submit", ...)` -> `form_submit`
- Unknown `track(eventType)` -> `custom`

`time_on_page` is emitted automatically on screen transitions and periodic heartbeat flushes.

## Notes

- Queue is persisted in `SharedPreferences` for retry after app restart.
- Session timeout follows web tracker default (`43200` minutes / 30 days).
- Backend requires `page_visit_id` + `event_seq` for dedupe; SDK always includes both.

## Next implementation steps

- App lifecycle plugin helpers (`ProcessLifecycleOwner`)
- Rich helper APIs (`trackClick`, `trackFormSubmit`, `trackScroll`)
- Optional WorkManager-based reliable background flush
- Instrumentation tests

## Publishing

### Publish to local Maven

```bash
cd mobile-sdks/android/crm247-tracker
gradle :tracker:publishReleasePublicationToMavenLocal
```

### Publish to remote Maven repository

Set repository credentials and URLs via env vars (or Gradle properties):

```bash
export CRM247_MAVEN_RELEASES_URL="https://your.repo/releases"
export CRM247_MAVEN_SNAPSHOTS_URL="https://your.repo/snapshots"
export CRM247_MAVEN_USERNAME="username"
export CRM247_MAVEN_PASSWORD="password"
```

Optional signing (in-memory PGP):

```bash
export ORG_GRADLE_PROJECT_signingInMemoryKeyId="YOUR_KEY_ID"
export ORG_GRADLE_PROJECT_signingInMemoryKey="BASE64_OR_ASCII_ARMORED_KEY"
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="YOUR_KEY_PASSWORD"
```

Run publish:

```bash
cd mobile-sdks/android/crm247-tracker
gradle :tracker:publishReleasePublicationToCrm247RemoteRepository
```

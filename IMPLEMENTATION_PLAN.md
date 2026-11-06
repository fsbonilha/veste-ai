# Veste-AI — Android MVP Implementation Plan

Derived from `PRD.md`. Scope is the MVP only: local-first wardrobe, baseline photo,
virtual fitting room, lookbook. Everything in PRD §6 is explicitly out of scope.

---

## 1. Scope

### In scope
| PRD § | Capability |
| :--- | :--- |
| §4.1 | Onboarding carousel + camera/media access |
| §4.2 | Baseline mannequin photo capture with framing overlay, re-take from settings |
| §4.3–4.4 | Garment capture (photo/gallery), crop hint, manual category tagging |
| §3 | Local taxonomy + metadata persistence (Room) |
| §5.1 | Fitting room canvas, category-tabbed selection drawers, selection rules, Try On, result actions |
| §5.2 | Lookbook grid, card details, delete / favorite / re-load |
| §2 | Repository-decoupled storage + API-agnostic VTON adapter |

### Out of scope (do not build)
Auth, cloud sync, S3, TFLite auto-tagging, recommendations, weather, headwear
quality work, social sharing beyond the system share sheet.

---

## 2. Toolchain (verified 2026-11-06)

| Component | Version | Note |
| :--- | :--- | :--- |
| Kotlin | 2.4.21 | released 2026-10-08; 2.5.0 lands Dec 2026 — stay on 2.4 |
| AGP | 9.5.0 | October 2026; requires Gradle ≥ 9.6.0, JDK 17, max API 37 |
| Gradle | 9.6.0 | |
| compileSdk | 37 | Compose 1.12+ requires compileSdk 37 and AGP ≥ 9.2 |
| targetSdk | 36 | Play rejects new apps below 36 since 2026-08-31 |
| minSdk | 26 | per PRD |
| Compose BOM | 2026.09.00 | compose 1.12.1, material3 1.4.0 |
| Room | 2.8.5 | via KSP, `room` Gradle plugin for schema export |
| CameraX | 1.6.2 | 1.7.0 is at rc02 — stay on stable |
| CameraX Viewfinder | 1.6.2 | own group `androidx.camera.viewfinder`; use `viewfinder-compose`, not the old `camera-viewfinder` artifact |
| Hilt (androidx) | 1.4.0 | Dagger 2.60.1 (min Hilt SDK 23, min AGP 9) |
| Coil | 3.6.3 | |
| Navigation (Compose) | 2.10.2 | **in maintenance mode** (critical fixes only). Navigation3 is 1.2.1; see note below |
| DataStore | 1.3.0 | `datastore-preferences` + `datastore-tink` for the encrypted API key (§6.4) |
| Networking | OkHttp + kotlinx.serialization | deliberately no Retrofit: one endpoint, two deps |
| Testing | JUnit4, kotlinx-coroutines-test, Turbine, MockWebServer, Compose UI test, Robolectric | |

Rule: pin every version in `gradle/libs.versions.toml`. No dynamic versions — AGP's
own docs warn against `9.5.+`-style ranges. Every row above was checked against the
AndroidX / Kotlin / AGP release pages on 2026-11-06, but the *set* has not been
through a build; the first task of M0 is to prove it resolves.

Navigation choice: `Navigation 2.x` is officially in maintenance mode, but
`Navigation3` (1.2.1 stable) is a new paradigm with far less production mileage.
Take Navigation 2.10.2 with the type-safe `@Serializable` route API — the nav graph
here is four destinations and is not a place to take a bet. Revisit at post-MVP.

Kotlin 2.5.0 (December 2026) will land mid-project. Do not adopt it: it is a language
release and would drag Compose and KSP along with it.

---

## 3. Architecture

Single `:app` module, package-by-feature, with hard boundaries between `domain`,
`data`, and `feature`. Multi-module split is deferred — it buys nothing at MVP size
and the boundaries below already make the split mechanical later.

```
app/src/main/java/com/vesteai/
├── VesteApplication.kt              @HiltAndroidApp
├── MainActivity.kt
├── core/
│   ├── di/                          Hilt modules (db, media, vton, dispatchers)
│   ├── media/                       ImageStore, ImageImporter, ImageNormalizer
│   ├── result/                      DataResult<T> (Success/Failure)
│   ├── error/                       AppError → user-visible string
│   └── ui/                          theme/, shared Composables (EmptyState, ErrorBanner)
├── domain/
│   ├── model/                       Garment, GarmentCategory, Outfit, BaselinePhoto
│   ├── fitting/                     FittingRoomSelection (pure, unit-tested)
│   ├── vton/                        VtonEngine, VtonRequest, VtonResult,
│   │                                VtonCapabilities, VtonError
│   └── repository/                  WardrobeRepository, LookbookRepository,
│                                    ProfileRepository  (interfaces only)
├── data/
│   ├── db/                          VesteDatabase, entities, DAOs, converters
│   ├── repository/                  Room-backed implementations
│   ├── prefs/                       UserPreferences (DataStore)
│   └── vton/
│       ├── mock/                    MockVtonEngine (debug default)
│       └── pixelapi/                PixelApiVtonEngine, PixelApiClient, dto/,
│                                    PixelApiCategoryMap, PixelApiErrorMapper
└── feature/
    ├── onboarding/
    ├── baseline/
    ├── wardrobe/                    list, capture, tagging
    ├── fittingroom/
    ├── lookbook/
    └── settings/
```

Dependency direction: `feature → domain ← data`. `domain` has no Android imports
except where unavoidable (`Uri` is *not* used in domain models — see §5).

---

## 4. Data layer

### 4.1 Entities

`GarmentEntity` (PRD §3 metadata schema, plus what the UI actually needs):

| Column | Type | Notes |
| :--- | :--- | :--- |
| `id` | String PK | UUID |
| `imagePath` | String | **relative** path under `wardrobe/` (see §5) |
| `category` | String | enum name, converted by `CategoryConverter` |
| `createdAt` | Long | epoch millis; pinned in tests |
| `isFavorite` | Boolean | |
| `displayName` | String? | optional, defaults null |

`OutfitEntity` (lookbook — not in PRD schema, required by §5.2):
`id`, `resultImagePath`, `createdAt`, `isFavorite`.

`OutfitGarmentCrossRef` — many-to-many (`outfitId`, `garmentId`), required so a
lookbook card can show thumbnails and re-load constituents. PRD §5.2 depends on it.
Foreign keys: `outfitId → outfit(id) ON DELETE CASCADE`, `garmentId → garment(id)`
**without** cascade — the cross-ref must never outlive its outfit, and a garment
cannot be deleted while cross-refs still point at it (see §4.3, which deletes the
outfits first). Index both columns; the reverse lookup `garmentId → outfits` is on the
hot path of the delete dialog.

Room: `@Database(version = 1, exportSchema = true)`, schemas checked in at
`app/schemas`. No destructive fallback — a migration test harness is wired from v1
so v2 never becomes a wipe.

### 4.2 DAOs

- `GarmentDao`: `observeByCategory`, `observeAll`, `getByIds`, `upsert`, `delete`, `setFavorite`
- `OutfitDao`: `observeAll`, `getWithGarments`, `insert(outfit, crossRefs)`, `delete`, `setFavorite`,
  `countContaining(garmentId): Int`, `deleteContaining(garmentId): Int`

Observable reads return `Flow` (single source of truth = DB). One-shot reads
(`getByIds`, used to build a VTON payload) are `suspend` and return a snapshot, not a
stream. Writes are `suspend` and run on `Dispatchers.IO` via a Hilt-provided qualifier.

### 4.3 Deletion semantics (decided, see §10)
Deleting a garment **also deletes every lookbook that contains it**, after explicit
confirmation. This supersedes the earlier "card degrades, re-load disabled" design —
there is no partial state left to render, so the lookbook never displays a card whose
constituents are missing.

**Flow** (`GarmentDeletionUseCase`):

1. `countContaining(garmentId)` on `OutfitDao`.
2. If `count == 0` → ordinary confirm ("Delete this garment?"). No cascade warning,
   because nothing else is lost.
3. If `count > 0` → destructive confirm using the plural resource:
   `deleting_garment_cascades_lookbooks` →
   *"Deleting this garment will also delete %d lookbooks that contain it.
   Do you really want to proceed?"* — 1 word form for `count == 1`.
4. On confirm, a **single `@Transaction`**: delete the garment, delete all
   `OutfitEntity` rows containing it (cross-refs cascade via FK), then — after the
   transaction commits — delete the garment image file and every deleted outfit's
   result image file from `ImageStore`.
5. The count is advisory: the transaction re-derives which outfits to delete rather
   than trusting the number shown in the dialog, so a stale count can never orphan a
   lookbook.

**Consequence worth stating in the UI copy:** a lookbook render costs 32 credits
(64 for an outfit) to generate, and a cascade delete destroys it permanently with no
undo. The confirm dialog must say the looks are gone for good, not just that they are
being removed.

Delete the *outfits before the garment* inside the transaction so the non-cascading
`garmentId` FK never blocks the delete.

---

## 5. Media storage

All media lives in app-internal storage — no `WRITE_EXTERNAL_STORAGE`, no scoped-media
workarounds, nothing visible outside the app:

```
filesDir/
├── baseline/baseline.jpg
├── wardrobe/<uuid>.jpg
└── looks/<uuid>.jpg
```

- Store **relative filenames**, not absolute paths, in the DB. The app data dir path
  changes across installs/restores; storing absolutes (as PRD §3 implies) produces dead
  rows. `ImageStore.resolve(relative): File` is the single accessor.
- `ImageNormalizer` on every import: decode with `BitmapFactory` + `ExifInterface`,
  apply rotation, then **aspect-correct to portrait** (center-crop or letterbox-pad to
  the target ratio, never stretch), enforce a **≥300×400 floor** by rejecting too-small
  inputs at import with a clear message, scale the long edge to 1024 px max, JPEG q85,
  write atomically to `*.tmp` then rename. This bounds disk growth, removes the main
  cause of sideways model output, and prevents a user's landscape photo from failing at
  the API as a `422 wrong_aspect_ratio` that the app can only report opaquely.
- `ImageImporter` accepts both a camera-captured file and a photo-picker `Uri`
  (copied via `contentResolver.openInputStream`) so both entry points share one path.
- Deleting a garment deletes its file after the DB row commits; orphan-file sweep
  runs on app start in the background.

---

## 6. VTON adapter (PRD §2)

### 6.1 Contracts

```kotlin
enum class GarmentCategory { HEADWEAR, TOP, BOTTOM, FULL_BODY, FOOTWEAR }

data class VtonCapabilities(
    val supportedCategories: Set<GarmentCategory>,   // PixelAPI: TOP, BOTTOM, FULL_BODY
    val supportsTopBottomOutfit: Boolean,            // PixelAPI: true, via /outfit
    val maxParallelJobs: Int,                        // PixelAPI free tier: 3
    val requiredAspect: AspectRequirement,           // PixelAPI: portrait, ≥300×400
)

sealed interface VtonError {
    data object NotConfigured : VtonError
    data object AuthenticationFailed : VtonError
    data object UnsupportedCombination : VtonError
    data object NoNetwork : VtonError
    data object Timeout : VtonError
    data class TooManyConcurrentJobs(val guidance: String) : VtonError
    data class ProcessingBusy(val retryable: Boolean) : VtonError
    data class InputTooSmall(val receivedSize: String?) : VtonError
    data object WrongAspectRatio : VtonError
    data object BodyNotDetected : VtonError
    data object InvalidImage : VtonError
    data class JobFailed(val guidance: String?) : VtonError
    data class ServerError(val status: Int) : VtonError
}

interface VtonEngine {
    val capabilities: VtonCapabilities
    val displayName: String
    suspend fun render(request: VtonRequest): DataResult<VtonResult>
}
```

`VtonRequest` carries the baseline image bytes plus the selected garments keyed by
`GarmentCategory`. Passing bytes rather than `Uri`/URL strings keeps each engine
independent of how a provider ships images (base64 JSON, multipart, or S3 presigned
URLs), and keeps the contract stable when the backend changes.

`render` is **suspending and internally long-running** — PixelAPI is async (submit,
then poll every 2–3 s, ~35 s single / ~90 s outfit). Implementations must take a
`kotlinx.coroutines` cancellation cue from the caller's scope so Cancel in the UI
actually stops polling, and must not block a dispatcher thread while waiting.

`capabilities` is what makes the PRD's "API-agnostic shell" honest: the UI disables
unsupported categories instead of promising a result the backend can't produce.

### 6.2 Implementations

1. `MockVtonEngine` (default in debug, selectable in settings): composites the
   baseline with garment thumbnails via Canvas, 800 ms artificial delay. Unblocks all
   UI work and all UI tests without network.
2. `PixelApiVtonEngine` — **the first-version default**, specified in §6.5.
3. `IdmVtonEngine` — documented alternative, not built in the MVP.

PixelAPI handles top+bottom natively via `/v1/virtual-tryon/outfit` (two server-side
passes, bottom then top, to avoid waistband bleeding). **No client-side chaining and no
`CompositeVtonEngine` decorator in the MVP.** A `VtonEngine` that lacks native outfit
support would need one later, but nothing in the MVP does.

### 6.3 Selection → request rules (pure, unit-tested)

`FittingRoomSelection.toggle(item)` returns a new immutable state:

| Action | Result |
| :--- | :--- |
| toggle `TOP` / `BOTTOM` while a `FULL_BODY` is active | `FULL_BODY` cleared, new item active |
| toggle `FULL_BODY` while `TOP` or `BOTTOM` active | both cleared, `FULL_BODY` active |
| toggle `HEADWEAR` / `FOOTWEAR` | other slots untouched |
| toggle an already-active item | item removed |
| toggle `BOTTOM` while `BOTTOM` active | replaces (one per slot) |

This function is the single source of truth for PRD §5.1 selection logic; no
selection branches anywhere in the ViewModel or Composables.

### 6.4 Bring-your-own-key (decided: BYOK)

The MVP ships **no bundled credential**. The user supplies their own API key and
base URL in Settings; without a verified key the app runs on `MockVtonEngine` and
Settings explains why rather than failing at Try On.

**Storage.** The key never touches the Room DB, plaintext DataStore preferences,
logcat, or a crash report. Use `datastore-tink` (`AeadSerializer` over
`EncryptedFile`), which is backed by the Android Keystore and was promoted out of
`alpha` with DataStore 1.3.0. Explicitly **not** `EncryptedSharedPreferences` — it
is deprecated and never left `alpha`. If Tink init fails on a corrupt keyset, treat
the key as absent, prompt for re-entry, and wipe the keyset rather than crash.

**Validation on save.** PixelAPI documents no zero-cost health route, and a real
validation call costs 32 credits — charging users to save a key is not acceptable.
So: persist the key after a *local* format check (non-empty, `pk_live_` prefix), and
let the first Try On be the source of truth. A `401` then means "key rejected" and must
show an inline "update key" affordance plus a link to `pixelapi.dev/app` instead of a
dead end. The key is never logged on failure.

**Boundary rules.**
- Keys are reached only through `VtonCredentialProvider`. Only `PixelApiVtonEngine`
  (and future engine impls) may depend on it; `domain` and `feature` never see the
  raw string.
- `redactHeader("Authorization")` on the logging interceptor, and keep the
  interceptor out of release builds entirely.
- No key in the manifest, `gradle.properties`, or any checked-in file; a `detekt`
  rule fails release builds on hardcoded secret patterns.
- Settings shows the key masked (`••••1234`) with a "Remove key" action that erases
  the entry and falls back to Mock.

**Privacy copy.** The key is the user's, so requests bill to their own account. Say
that plainly in the consent screen and Settings, next to the "photos leave the
device" warning.

### 6.5 First-version provider: PixelAPI (decided)

The MVP ships against **PixelAPI** (`https://pixelapi.dev`). This is a *default*, not
an architectural commitment: it is one implementation behind `VtonEngine`, selected by
exactly one Hilt binding (§6.6).

**Contract recorded 2026-11-06** from the saved docs at `~/Downloads/pixelapi-docs.html`
(the marketing page `vton-api-vs-zyler.html` is thinner and partly inaccurate — the
reference doc wins). Facts below are transcribed, not inferred.

#### Endpoints

| | Single garment | Top + bottom |
| :--- | :--- | :--- |
| Submit | `POST https://api.pixelapi.dev/v1/virtual-tryon` | `POST /v1/virtual-tryon/outfit` |
| Body | `person_image`, `garment_image`, `category` | `person_image`, `upperbody_garment`, `lowerbody_garment` (+ optional `n_steps`, `image_scale`, `webhook_url`) |
| Poll | `GET /v1/virtual-tryon/jobs/{job_id}` | same |

- JSON body, images **base64-encoded strings with no `data:` prefix** — not multipart.
- `category` ∈ `upperbody` \| `lowerbody` \| `dress`. So `TOP→upperbody`,
  `BOTTOM→lowerbody`, `FULL_BODY→dress`.
- Submit returns `{job_id, status:"queued", credits_used}`; polling returns
  `{status, result_image_b64, inference_time_ms}` where status ∈
  `queued|processing|completed|failed`. Result is **inline base64 PNG**.
- **Asynchronous by design.** `inference_time_ms` ≈ 35000 in their example; the outfit
  endpoint advertises `eta_seconds: 90`. Poll every 2–3 s.
- Auth `Authorization: Bearer <key>`, key prefix `pk_live_`.
- **`User-Agent` is mandatory** — their docs warn requests without one may be blocked
  by their CDN. Hardcode `VesteAI/1.0 (Android)`.
- Cost: 32 credits single, 64 credits outfit. Free trial: 300 credits, 20 req/min,
  3 parallel jobs.

#### Input constraints — these propagate into the media layer

- Person photo must be **portrait**; landscape returns `422 wrong_aspect_ratio`.
- Minimum ~300×400 px; their own docs also mention a 100 px floor (§5 normalizer
  should target the stricter 300×400).
- Max useful 1024×1024; plan-dependent upload cap of 10 MB.
- Garment should be flat-lay/product shot on a light background.

**Consequence for §5:** `ImageNormalizer` must not merely downscale. It must also
**crop/pad to a portrait aspect ratio** and reject or re-frame anything under 300×400,
otherwise a user's landscape baseline photo fails at the API with a 422 the UI can only
report as "server rejected". Do this at import time, once, not per request.

#### Error taxonomy → `VtonError`

| HTTP | `detail.error` | Maps to |
| :--- | :--- | :--- |
| 422 | `image_too_small` | `InputTooSmall(receivedSize)` |
| 422 | `wrong_aspect_ratio` | `WrongAspectRatio` |
| 422 | `body_segmentation_failed` | `BodyNotDetected` |
| 422 | `invalid_category` | `UnsupportedCombination` |
| 400 | `invalid_image` | `InvalidImage` |
| 401 | — | `AuthenticationFailed` |
| 429 | — | `TooManyConcurrentJobs` (limit 3 on free tier) |
| 503 | `processing_out_of_memory` | `ProcessingBusy(retryable)` |

Error body shape is `{"detail": {"error", "message", "guidance", "received_size"?}}` —
surface `guidance` to the user directly rather than inventing copy.

#### Capability answer (resolves the open question)

PixelAPI supports `upperbody`, `lowerbody`, and `dress` — **bottoms are supported**.
Unsupported by this provider: `HEADWEAR` and `FOOTWEAR` only. So PRD §3's Core MVP is
largely intact; headwear was already post-MVP per the PRD, and only footwear is a real
gap. `capabilities` reports accordingly, and the UI disables exactly Footwear/Headwear
with an inline reason — never a hardcoded list.

**Top + bottom needs no client-side chaining.** Use `/v1/virtual-tryon/outfit`, which
runs both passes server-side (bottom first, then top) specifically to prevent cloth
bleeding at the waistband. A `CompositeVtonEngine` decorator is therefore *not* built
in the MVP — it would be slower, more expensive in round-trips, and worse at the seam.

### 6.6 Modularity requirement (hard constraint)

Swapping PixelAPI out must be **one class + one binding**, with no edits anywhere else.
This is a review gate for M4/M7, not an aspiration:

| Concern | Where it lives | Must NOT leak into |
| :--- | :--- | :--- |
| Endpoint, paths, auth header | `data/vton/pixelapi/PixelApiClient` | UI, `domain` |
| Provider DTOs + serialization | `data/vton/pixelapi/dto/` | any `domain` model |
| Category string mapping | `PixelApiCategoryMap` (provider ↔ `GarmentCategory`) | `domain/model` |
| Multipart/body building, base64 vs raw | `PixelApiRequestBuilder` | ViewModels |
| Error-code → `VtonError` mapping | `PixelApiErrorMapper` | callers |
| Base URL, retries, timeouts | `PixelApiConfig` + OkHttp setup | feature code |

Rules:

- `domain` and `feature` may reference only `VtonEngine`, `VtonRequest`,
  `VtonResult`, `VtonCapabilities`, `VtonError`. No `pixelapi` package import is
  reachable from either — enforce with a Konsist/`detekt` architecture rule in CI.
- Every provider-specific string, path, and enum lives in that provider's package.
  Nothing provider-shaped appears in `strings.xml` except user-facing copy.
- Adding a provider = new package + `@Binds` entry + `capabilities` + a
  MockWebServer test suite. No existing file changes. **Swapping should be a
  one-line diff in the Hilt module**; M7's definition of done includes doing the swap
  once on a throwaway branch to prove it.
- `MockVtonEngine` is bound in debug by default so no UI work is ever blocked on a
  provider decision.
- The engine is chosen at the `VtonEngineModule` binding, not by `if (provider == ...)`
  branching. A runtime "engine picker" in Settings, if we want one, resolves the
  binding from `capabilities`/`displayName` — never from a provider name string.

---

## 7. Screens & navigation

Navigation 2.10.2 with the type-safe `@Serializable` route API (see §2 for why not
Navigation3).

```
Onboarding (carousel → permissions → baseline capture → first garment)
  └── on completion, lands on Wardrobe with an empty state, not Fitting Room

Home — bottom bar: Fitting Room | Wardrobe | Lookbook | Settings
├── FittingRoom (canvas + selection drawer + result sheet)
├── Wardrobe    (grid, add garment → capture → tagging)
├── Lookbook    (grid → detail)
└── Settings    (re-take baseline, API key entry, engine toggle,
                 clear data, privacy)
```

`Wardrobe` is a **first-class tab**, not a screen reached only from onboarding.
PRD §4.3/§5 imply cataloging is a continuing activity, so it must stay reachable at
all times; without the tab it is unreachable once onboarding ends.

Navigation notes:

- Four tabs; Material3 `NavigationBar` supports 3–5. Do not add a fifth without
  reconsidering — five is the point where labels start truncating at font scale.
- Fitting Room is the start destination once a baseline photo exists; Wardrobe is the
  start destination when the wardrobe is empty, so a fresh user is not dropped into a
  canvas with nothing to wear.
- Each tab keeps its own back stack (`saveState`/`restoreState` per tab), so leaving
  and returning to Lookbook doesn't lose scroll position or a detail screen.
- The Try On result sheet and the garment capture flow are **not** tabs — they are
  modal destinations above the bar, which is hidden while they are open.

Details that need explicit handling:

- **Permissions** (PRD deviation approved): `READ_MEDIA_IMAGES` is not requested.
  `PickVisualMedia` grants no-persist `Uri` access with **no runtime permission** on
  API 26 through current, and the `androidx.core` dependency supplies a fallback
  picker where the system one is absent. `CAMERA` is still requested, only for
  in-app capture via CameraX. Update PRD §4.1 accordingly when it is next touched.
- **Fitting room render**: layered `Box` — baseline image, then semi-transparent
  selection chips. The real composite only appears after Try On. PRD's "highlights
  both items on the active canvas layer" is a highlight, not a fake try-on; do not
  alpha-blend garment cutouts over the body.
- **Try On progress**: PixelAPI is async — ~35 s for one garment, ~90 s advertised for
  an outfit — so this is a long operation, not a spinner. Show the job's real stage
  (`queued` → `processing` → `completed`) with an elapsed timer and a Cancel that
  cancels the polling coroutine. Overall timeout ~180 s, comfortably past their ETA.
  Disable Try On while a job is in flight, and surface the provider's own
  `guidance` text on failure instead of inventing copy.
- **Result sheet**: Save to Lookbook / Share (`FileProvider` + `ACTION_SEND`) /
  Discard. Discard deletes the temp result file.
- **Loading/empty/error states** on every screen; VTON errors map to user-facing
  copy through `AppError`, never a raw exception string.

---

## 8. Milestones

Estimates assume one developer, including tests but not design polish.

| # | Milestone | Deliverable | Est. |
| :--- | :--- | :--- | :--- |
| M0 | Scaffold | Gradle project, version catalog, Hilt, Compose theme, nav graph stub, CI (lint + unit tests + assemble), debug/release flavors | 1.0 d |
| M1 | Data layer | Entities, DAOs, converters, DB v1 + exported schema, repository impls, `ImageStore`, `ImageNormalizer`, `ImageImporter`; Robolectric DAO tests + normalizer tests (EXIF, downscale) | 2.5 d |
| M2 | Onboarding | Carousel, `CAMERA` permission flow (grant, deny, deny-forever), CameraX capture + framing overlay, baseline save/replace, `ProfileRepository` + DataStore flag | 1.5 d |
| M3 | Wardrobe | Picker/camera entry, crop-hint screen, tagging screen, wardrobe grid with category filter, favorite toggle, `GarmentDeletionUseCase` incl. cascade count + destructive confirm and transactional delete | 3.25 d |
| M4 | VTON contracts | `VtonEngine`, `VtonRequest`/`VtonResult`, `VtonCapabilities`, `VtonError`, `VtonCredentialProvider` (fake impl), `MockVtonEngine`, `FittingRoomSelection` + its full test table | 1.5 d |
| M5 | Fitting room | Canvas, tabbed selection drawer, capability-driven gating, Try On flow with progress/cancel/timeout, result sheet | 3.0 d |
| M6 | Lookbook | Outfit persistence, grid with thumbnail strip + date, detail, favorite, delete, re-load into fitting room | 2.0 d |
| M7 | PixelAPI engine + BYOK | `PixelApiClient` (OkHttp, JSON body, base64 images, mandatory `User-Agent`, Bearer auth), async submit+poll with cancellation, `/outfit` route for top+bottom, `PixelApiCategoryMap`, `PixelApiErrorMapper` for all 8 documented errors, encrypted key store (§6.4) + save-time validation, Settings entry (masked, remove), consent screen, MockWebServer suites incl. poll timeouts; prove the §6.6 one-class swap on a throwaway branch | 4.0 d |
| M8 | Hardening | Error/empty states audit, TalkBack pass (incl. `contentDescription` on images), `FileProvider` share, data-clear, R8 keep rules, release build + Play pre-launch report | 2.0 d |
| | **Total** | | **20.75 d** |

Each milestone ends with: all tests green on CI, `./gradlew lint assembleDebug`, and a
demoable build. M4 can run in parallel with M2/M3 once M1 lands.

---

## 9. Testing

| Layer | What | Tooling |
| :--- | :--- | :--- |
| Unit | `FittingRoomSelection` (every row of §6.3), request building, `VtonError` mapping, date formatting | JUnit4 + `kotlinx-coroutines-test` |
| DB | DAO CRUD, cross-ref joins, room schema export validation | Robolectric + in-memory Room |
| Cascade delete | `countContaining` matches reality; delete removes garment + all containing outfits + their image files; one-outfit and many-outfit counts; rollback leaves nothing half-deleted; stale count cannot orphan an outfit | Robolectric + in-memory Room, temp `filesDir` |
| Media | EXIF rotation applied, downscale bound respected, atomic write survives interrupted temp file | Robolectric |
| Network | JSON body + base64 fields, Bearer + `User-Agent` headers, each of the 8 documented provider errors → the right `VtonError`, poll loop terminating on `completed`/`failed`, cancellation mid-poll stops polling, overall timeout | MockWebServer + recorded fixtures |
| Provider isolation | No `pixelapi` import reachable from `domain`/`feature`; adding a stub engine requires no edits outside its own package + one binding | Konsist/detekt architecture rule |
| Credentials | Key round-trips through the encrypted store; corrupt keyset degrades to "absent" not crash; 401/403/404/timeout map to the right validation message; key absent from logs | Robolectric + MockWebServer |
| UI | Onboarding happy path, selection rules visible in UI, Try On with `MockVtonEngine`, lookbook re-load | Compose UI test, Hilt test rule |
| UI (destructive) | Cancel on the cascade dialog deletes nothing; confirm deletes exactly the counted looks; singular vs plural copy renders correctly | Compose UI test |
| Manual | Camera on API 26 / 33 / 36 device, permission denial + re-request, rotation, process death mid-tryon | device matrix |

Determinism: inject `Clock` for timestamps, inject `Dispatcher` for the mock engine's
delay, never assert on wall-clock or on real network.

---

## 10. Risks

**Decisions taken (2026-11-06):**

1. **Credentials — BYOK.** The user supplies their own key and base URL; no bundled
   credential ships. Details in §6.4.
2. **Photo picker instead of `READ_MEDIA_IMAGES`.** Approved. The picker removes a
   permission prompt and, beyond ergonomics, Play's Photo and Video Permissions
   policy allows broad media access only where it is core to the app's function —
   it is not core here, so a broad-read declaration is a plausible review rejection.
   PRD §4.1 needs a follow-up edit.
3. **Garment delete cascades into lookbooks.** Approved 2026-11-06, superseding the
   earlier degrade-in-place design: deleting a garment deletes every lookbook that
   contains it, behind an explicit count-bearing confirmation (§4.3). Side effect:
   the lookbook can never show a card with missing constituents, so that whole class
   of partial-render UI disappears.

**Still open (non-blocking):**

- Whether `FOOTWEAR` gets an MVP story. PixelAPI offers `upperbody`/`lowerbody`/`dress`
  only; footwear is genuinely unsupported. Either accept the gap (my recommendation —
  it's one category and the PRD already deferred headwear) or add a second engine.

**Technical risks:**

| Risk | Impact | Mitigation |
| :--- | :--- | :--- |
| PixelAPI has no footwear support | PRD §3 lists FOOTWEAR as Core MVP | `capabilities` disables + annotates it; accept the gap or add a second engine (§6.6 makes that one binding) |
| Base64 images inflate payloads ~33% and their cap is plan-dependent (10 MB free tier) | Large photos rejected or slow to upload | §5 normalizer caps at 1024 px / q85, which lands well under 1 MB; reject oversized input at import, not at submit |
| Async job model adds real latency (~35 s single, ~90 s outfit) and needs polling | Users think the app hung; battery/network waste | Explicit staged progress with elapsed timer, Cancel wired to coroutine cancellation, ~180 s timeout, jobs disabled while one is in flight |
| Their CDN blocks requests without a proper `User-Agent` | Mysterious 403s in the field | Hardcode `VesteAI/1.0 (Android)` in the OkHttp client and assert it in an interceptor test |
| Free tier allows only 3 parallel jobs → `429` | Confusing failure during rapid retries | Serialize Try On per user, disable the button while in flight, map `429` to actionable copy |
| Person photo must be portrait or the API 422s | Every landscape baseline photo fails | §5 normalizer enforces portrait at import, so this is unreachable from the UI |
| Provider goes down or changes shape | Broken try-on | Mock engine always available; engine swap is one binding (§6.6); vendor specifics isolated in one package |
| Photos leave the device on remote try-on | Privacy promise in PRD §1 | Explicit consent before first upload, no upload at all in Mock mode, clear data flow in Settings |
| Compose 1.12 needs compileSdk 37 while Play needs targetSdk 36 | Version churn mid-project | Both pinned in §2; re-check only at M0 |
| On-device storage growth | Unbounded disk | 1024 px normalization, orphan sweep, "clear data" in Settings |
| Users can't obtain or correctly enter an API key | Dead-on-arrival real try-on | Mock engine always works with zero setup; key entry validates before saving and names the exact failure; Settings is reachable from the first Try On attempt |
| Key exposure on a lost/shared device | Credential theft | Keystore-backed encryption, masked display, explicit remove; document it as the user's own credential in the consent screen |

---

## 11. Definition of done (MVP)

- Fresh install → onboarding → baseline photo → at least one tagged garment with no
  dead ends.
- Fitting room selection rules match §6.3 exactly; `FULL_BODY` deselects top/bottom.
- Try On against the configured engine returns a composite; Save/Share/Discard all work.
- Lookbook persists across process death and re-loads constituents into the canvas.
- Deleting a garment never leaves an orphaned lookbook: either the looks are gone too
  (after a count-bearing confirm) or nothing was deleted.
- No absolute paths in the DB; no media outside app-private storage.
- Lint clean, unit + Robolectric + UI tests green, release build installable.
- No network call without an explicit consent screen.
- No API key in any log line, crash report, checked-in file, or plaintext prefs;
  removing the key returns the app to a fully working Mock mode.
- Baseline and garment images are portrait-corrected and ≥300×400 at import, so no
  user-reachable path produces a provider-side `422`.
- Swapping `PixelApiVtonEngine` for another engine touches one class + one Hilt
  binding; the architecture rule proves it.

# AgriX Offline — Android app

A native Android rebuild of the original AgriX web app (Next.js + Genkit +
Gemini). All AI calls run **on-device with Gemma**, via Google's
[MediaPipe LLM Inference API](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference).
There is no backend and no API key. The only network activity is a one-time
sign-in + model download on first launch — after that, the app is fully
offline.

## First-launch flow (what the farmer sees)

1. Open the app → "Set up your on-device AI" screen.
2. Tap **"Sign in & install AI model"** → an embedded Hugging Face login
   opens inside the app (no browser hand-off).
3. The moment sign-in succeeds, the app **automatically downloads and
   installs** the Gemma model — no file picker, no manual steps.
4. Fill in a short farm profile (stays on-device only).
5. Use any feature — crop suggestions, price prediction, pest control,
   disease diagnosis, soil advice, etc. — fully offline from then on.

A **"I already have the model file"** button remains as a fallback for
anyone who'd rather import a `.task` file directly (useful for testing, or
if Hugging Face's login page changes and breaks the automated flow).

### Why sign-in is unavoidable (please read this)

Google requires anyone using Gemma to accept its license. There is no way
for *any* app — this one, Google's own official "AI Edge Gallery" app, or
anyone else's — to skip that. What this app removes is everything *around*
that requirement: no separate browser, no manually locating the right file
on Kaggle/Hugging Face, no file picker. The farmer signs in once, and (the
very first time only) taps "Agree and access repository" on the Gemma model
page if they haven't already — then the app takes over completely.

**Important caveat on the sign-in mechanism:** `HFSignInScreen.kt` reads
Hugging Face's normal browser session cookie from an embedded WebView and
sends it as a `Cookie` header on the download request — the same thing a
browser does, just automated. This is different from a registered OAuth
app (which needs its own client ID/redirect URI and, typically, a backend
to keep secrets safe — not a fit for a fully offline, no-backend app). If
Hugging Face changes their login page structure, the "looks signed in"
heuristic in `HFSignInScreen.kt` may need a small tweak — the "Continue
anyway" button is there as a manual override if the heuristic doesn't fire.

## What changed vs. the original project

| Original (project_6) | This app |
|---|---|
| Next.js web app | Native Android app (Kotlin + Jetpack Compose) |
| `src/ai/genkit.ts` → Gemini 2.5 Flash via `googleAI()` cloud plugin | `ai/GemmaInferenceEngine.kt` → Gemma `.task` model via MediaPipe, runs on CPU/GPU on the phone |
| `src/ai/flows/*.ts` (Handlebars prompts sent to Gemini) | `ai/PromptTemplates.kt` (same prompts, shortened for a small on-device model, asked to return JSON) |
| Server Actions (`'use server'`) calling an external API | Everything happens in-process inside the app; `JsonExtractor.kt` parses the model's text reply |
| No install step needed (web) | First launch: sign in once, model installs itself automatically |

## Project structure

```
app/src/main/java/com/protoprojects/agrix/
  AgriXApp.kt                     Application class, holds the singleton Gemma engine + prefs
  MainActivity.kt                 Entry point
  ai/
    GemmaInferenceEngine.kt       Wraps MediaPipe LlmInference — load(), generate() (text + optional image)
    ModelDownloadManager.kt       Model acquisition: cookie-authenticated download OR import a local file
    HFModelSource.kt              Pins the exact Gemma .task file URL used for auto-download
    PromptTemplates.kt            Ported prompts from src/ai/flows/*.ts
    JsonExtractor.kt              Lenient JSON parsing for small-model output
  data/
    PreferencesManager.kt         Local-only DataStore: model install state, farmer profile, onboarding flag
  util/
    ImageUtils.kt                 Uri -> Bitmap loading for the photo picker
  viewmodel/
    ModelSetupViewModel.kt        Drives the first-run sign-in + install + model-load flow
    GemmaQueryViewModel.kt        Generic "prompt (+ optional photo) in → JSON out" ViewModel
  ui/
    theme/Theme.kt                Field-tool palette (loden green / harvest gold / soil rust) + serif/sans/mono type scale
    components/
      ContourMotif.kt             The one signature visual element — procedural contour-line header backdrop
      Ledger.kt                   Ledger-style section header + row, used by the dashboard
    navigation/AgriXNavHost.kt    model_setup -> onboarding -> dashboard -> feature screens
    screens/
      ModelSetupScreen.kt         Entry point of the setup flow (sign-in button + fallback import)
      HFSignInScreen.kt           Embedded Hugging Face login WebView
      OnboardingScreen.kt, DashboardScreen.kt (ledger-style grouped feature list)
      CropSuggesterScreen.kt, PricePredictorScreen.kt, PestControlScreen.kt
      SimpleTextFeatureScreen.kt      Generic text-only screen (soil/irrigation/livestock/profit)
      ImageAndTextFeatureScreen.kt    Generic screen with an optional photo attached (disease/produce)
      FeatureScreenCommon.kt      Shared loading/result/error UI
```

## Changing which Gemma model gets installed

Edit `ai/HFModelSource.kt`. It defaults to **Gemma 3 1B IT (int4, ~550 MB)**,
chosen because it runs comfortably on mid-range phones (4 GB RAM). If you
want higher-quality answers on devices with 6 GB+ RAM, point `MODEL_URL` at
a Gemma 2B build instead — just make sure it's a `.task` file built for
MediaPipe LLM Inference (Kaggle Models / Hugging Face `litert-community`
org both host these).

**Hugging Face file paths occasionally change** when a repo is re-uploaded.
If the auto-download starts failing with an HTTP error, open the model
repo page in a browser, copy the current file's download link, and update
`MODEL_URL`.

## Running inference offline

`GemmaInferenceEngine.generate(prompt, image)` wraps the prompt in Gemma's
instruction-tuned chat template (`<start_of_turn>user...<end_of_turn>
<start_of_turn>model`) before calling MediaPipe's
`LlmInference`/`LlmInferenceSession`, which runs the forward pass locally on
the GPU (falling back to CPU automatically on devices without a compatible
GPU delegate). Every feature screen builds a prompt with `PromptTemplates`,
sends it through `GemmaQueryViewModel`, and renders whatever JSON comes
back — the same shape the original Genkit flows returned, just produced
locally instead of over HTTPS.

**Photo input** is supported for the two screens where a picture actually
helps (Disease Detector, Produce Grading), via `ImageAndTextFeatureScreen`
and the system Photo Picker — no storage permission required. Under the
hood, `GemmaInferenceEngine` loads the model with `setMaxNumImages()` and,
when a photo is attached, enables the session's vision modality
(`GraphOptions.setEnableVisionModality(true)`) and calls
`session.addImage(...)` before the text query. A description is still
required on both screens, so they keep working even if the installed model
is a text-only build (`HFModelSource.MODEL_URL` currently points at Gemma 3
1B IT, which is text-only) — swap in a vision-capable `.task` file if you
want the photo to actually be read rather than just attached. If the loaded
model can't process an image, `generate()` surfaces that as a catchable
error with a farmer-facing message rather than crashing.

## Performance on mid-range devices

- `minSdk 26`, `arm64-v8a` + `armeabi-v7a` only — keeps the native binary
  small and avoids shipping x86 builds nobody in the field will use.
- Default model (`Gemma 3 1B int4`) has been shown to run at usable speed
  (several tokens/sec) on 4 GB RAM Android phones.
- `android:largeHeap="true"` is set since loading a few-hundred-MB model
  needs headroom beyond the default heap limit on some OEM skins.
- The engine is a singleton created once in `AgriXApp` and reused across
  every screen, so you only pay the model-load cost once per app session.

## Building

Standard Gradle Android project. Open the `AgriXOffline/` folder in Android
Studio (Koala or newer), let it sync, and run on a device or emulator with
API 26+. A physical mid-range phone is strongly recommended for testing —
on-device LLM inference is far slower on most emulators.

## Design

The visual language is a dark, "premium agritech" look — deep OLED black
background, translucent glass-panel cards, and a single glowing neon-green
(`#00FF66`) accent reserved for AI activity and primary actions
(`ui/theme/Theme.kt`, `ui/components/GlassComponents.kt`). The dashboard is
a bento-grid layout with a hero "AI Crop Diagnosis" card, a searchable tool
list, and a floating pill-shaped bottom nav (Dashboard / Scan / Profile —
`ProfileScreen.kt` is a small read-only view of the on-device farmer
profile). Every result panel doubles as a lightweight "diagnosis complete"
card, surfacing the model's self-reported confidence score as a glowing
meter when the prompt asked for one (see `ResultArea` in
`FeatureScreenCommon.kt`).

Two deliberate simplifications from the original design brief: true
backdrop blur (CSS `backdrop-filter`) needs Android 12's RenderEffect API,
which would have meant dropping this app's minSdk 26 target, so "glass" is
approximated with a translucent surface + thin border instead — it reads as
glass without that dependency. And there's no bundled Inter/SF Pro font
file; headings/body text use the platform's default sans (Roboto) at bold/
medium weights, so there's no font asset that can go missing at build time.

### A note on "zero errors, verified"

This scaffold was built and desk-checked without access to a real Android
SDK/Gradle environment — every file was reviewed by hand against the
documented APIs, plus real Gradle build logs where they were provided. This
pass fixed, on top of the earlier design overhaul:

- **App stuck at a loading spinner on launch**: a previous fix for the
  "always reopens on setup" bug made `modelReady`/`onboarded` nullable
  (`null` = "DataStore hasn't reported in yet") and held the UI on a
  spinner until both were non-null. That introduced a worse bug: on a
  **fresh install**, neither preference key exists yet, so the flow's
  first *real* emission is also `null` — indistinguishable from "still
  loading" — so the spinner never went away. Fixed properly in
  `AgriXNavHost`: the flows are back to plain non-null `Flow<Boolean>`
  (defaulting `false` when a key is absent, like any normal DataStore
  read), and the gate now explicitly awaits one real emission via
  `combine(...).first()` in a `LaunchedEffect` — there's no ambiguous
  placeholder value left to collide with.
- **`tasks-vision` dependency didn't resolve**: `0.10.24` was never
  published for that artifact — Google publishes `tasks-genai` and
  `tasks-vision` on separate schedules, and `tasks-vision` jumped from
  `0.10.15` straight to `0.10.26`. Pinned to `0.10.26` (confirmed
  published); `tasks-genai` stays at `0.10.24`. See the comment in
  `app/build.gradle.kts` for the fallback if this ever surfaces as a
  runtime native-library mismatch instead of a build error.
- **Backend robustness pass**: `JsonExtractor` was rewritten from a naive
  `indexOf('{')`/`lastIndexOf('}')` span (which breaks the moment a brace
  character shows up anywhere else in the response, including inside a
  string value) to a proper balanced-brace scan that respects string
  literals and escapes. `GemmaQueryViewModel` now retries once, with a
  sharper "reply with ONLY JSON" reminder, if the first response doesn't
  parse — recovering most of the cases where a small on-device model wraps
  its answer in a stray sentence. Every `PromptTemplates` function now asks
  for a `confidence` score (0-1), surfaced in the UI as a real meter rather
  than decoration.
- Earlier fixes (nav-always-reopens root cause, load-before-navigate gate,
  Gemma chat-template formatting, re-added image/vision support,
  `PreferencesManager.farmerProfile` nullability) are unchanged by this
  pass — see git history / prior notes in this file for those.

If a fresh build still surfaces something, it's most likely to be a
MediaPipe API detail that shifted between versions — everything else in
this project uses stable, well-documented AndroidX/Compose APIs.

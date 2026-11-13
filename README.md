# TV Assistant (Gemini fork)

This fork of [mertmr/tv-assistant](https://github.com/mertmr/tv-assistant) (MIT) adds **Google Gemini**
as an AI provider. Everything still runs on the TV. The upstream ChatGPT path is unchanged.

## Gemini provider (fork addition)

- **Provider choice.** If a Gemini API key is stored, Gemini is the default. ChatGPT is used only when
  no key is stored, or when you sign in to ChatGPT and turn on **Settings → Gemini → Prefer ChatGPT
  when signed in**.
- **API.** Gemini API `generateContent` (`v1beta`) with a Google AI Studio API key in the
  `x-goog-api-key` header. Function calling works like the ChatGPT path: each tool definition becomes a
  `functionDeclarations` entry. Its JSON Schema is passed unchanged as `parametersJsonSchema`.
  Request and tool budgets, the final-round "report now" turn (`functionCallingConfig.mode = NONE`),
  tool-output truncation and conversation memory are all shared with the ChatGPT path. Model turns
  are kept verbatim, so Gemini 3 thought signatures come back unchanged.
- **Models.** The default is `gemini-3.8-flash`. If a request fails with HTTP 404, 429 or 5xx, or
  times out, that request is retried once on `gemini-3.5-flash-lite`. You can pick another model under
  **Settings → Gemini → Choose Gemini model**, which lists the models your key can use, or pass
  `--gemini-model` to the installer.
- **Screen vision.** A `screen_see` screenshot is sent as an `inlineData` JPEG part next to the
  function response. Only the newest image is kept.
- **Language.** In Gemini mode the system prompt has an extra section for Puerto Rican Spanish and
  Spanglish dictation: misheard words, titles spelled the way they sound, and local vocabulary. The
  assistant answers in short, plain Spanish. App and show titles are kept in their original spelling.
  App UI text stays in English.
- **Key storage.** The key is encrypted with AES-GCM using the same Android Keystore key as the
  ChatGPT OAuth tokens. It is held in a separate preference file (`gemini_vault`), and
  `allowBackup=false` still applies.

### Provisioning the key over ADB (nobody types it on the TV)

```bash
# key in a local file that is never committed (or export GEMINI_API_KEY and use --gemini-key-env)
python3 install.py --device <tv-ip>:5555 --port 5037 --enable-navigation --enable-playback \
  --gemini-key-file ~/path/to/gemini.key
# change only the key or model later, without reinstalling:
python3 install.py --device <tv-ip>:5555 --port 5037 --skip-install --no-launch --gemini-key-file ~/path/to/gemini.key
python3 install.py --device <tv-ip>:5555 --port 5037 --skip-install --no-launch --clear-gemini-key
```

The installer sends the key to `KeyReceiver` through an `adb shell` session's stdin, so it does not
show up in the computer's process list. The receiver is protected by `android.permission.DUMP`,
which the ADB shell holds and ordinary apps cannot get, so no other app on the TV can replace the
key. On the TV, the key is visible in the `am broadcast` command line for the moment that command
runs. The receiver never logs the key or echoes it back.

### Upgrading from upstream

This fork keeps the package name `dev.mert.tvassistant`. An upstream build signed with a different
key must be uninstalled before this one is installed (`adb uninstall dev.mert.tvassistant`).

### Build notes

`./build.sh` needs a JDK on `PATH`. Homebrew `openjdk@21` works:
`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`. The debug signing key is
created once in `build/debug.keystore` and is git-ignored. Keep it, because later updates must be
signed with the same key.

---

A native Android TV / Fire OS assistant using the remote's keyboard dictation. The APK runs entirely on the TV; the Mac is needed only to build, install, or enable permissions on Fire TV. When connected to ChatGPT with plan usage enabled, every command is interpreted by AI using a bounded Responses API tool loop. Basic local parsing is available only when disconnected or when you explicitly enable local-only mode.

## Start using it

1. Open **TV Assistant**. Its system keyboard opens automatically.
2. Hold the remote mic and speak, then choose **Next** or **Search**.
3. When connected, AI interprets your full request and chooses tools. Check **Actions** for results; **Stop** cancels an AI stream and stops subsequent actions.
4. Open **Settings → Continue with ChatGPT** for AI tasks. Authenticate in the browser **on the TV** and choose whether to grant ChatGPT plan usage. The callback is bound to the TV's loopback address, so completing that URL on a phone would not reach the TV.
5. Return to TV Assistant. Settings shows the active connection, available model picker, request/step limits, local-only mode, spoken answers, and usage-management link.

## Raw microphone experiment (0.2.1)

**ChatGPT plan-sharing does not support the transcription API or audio input.** The existing
subscription login cannot be used for OpenAI speech-to-text. See the official
[preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations).
There is no hidden API-key/billing fallback, audio upload, or automatic command execution from this
experiment. Local Whisper is a possible future alternative, not an implemented feature.

**Mic test** is an explicit, local-only diagnostic: after confirmation and Android microphone
permission, it measures PCM sample counts/RMS/peak for at most eight seconds. It retains no recording,
obtains no account token, and makes no network request. Stop, leaving the activity, or process death
ends capture. Settings cycles the test source (VOICE_RECOGNITION/MIC/VOICE_COMMUNICATION/DEFAULT).
Optional Bluetooth SCO is **off by default**; when enabled it may temporarily change routing and
adds a maximum three-second connection wait. Attempts are cleaned up even on timeout/error.

Raw Fire TV remote audio access has **not** been tested for this implementation. Nonzero levels may
come from a different microphone, noise, or playback; they do not establish remote speech capture.
Holding Alexa can still start Amazon's system voice UI and interrupt the test. Keep keyboard dictation
until capture and routing are independently confirmed. No new TV installation/testing was performed
for 0.2.1 because another thread owns the device.

Examples that work without a ChatGPT connection:

- “Open Stremio and find The Wire” — looks for an exact title and opens its detail page; otherwise opens search results.
- “Open Stremio and find South Park season 3 episode 5” — resolves the catalog's actual episode ID, opens the episode directly with autoplay disabled, and verifies Stremio's series/episode headings. No season-by-season remote navigation or automatic stream selection.
- “Open example dot com” — opens Silk if installed, otherwise the default browser.
- “Browse example.com” — opens the assistant's internal browser.
- “Search The Wire in Stremio” or “Find jazz on YouTube.”
- “Pause,” “resume,” “rewind,” or “next track” — requires an active player advertising that action and playback permission.
- “Set volume to 40 percent,” “mute,” or “volume down.”
- “Weather in Istanbul,” “calculate (12+8)*3/2,” or “what is the time.”
- “Set a timer for 2 minutes called tea,” “show notes,” “show routines,” or “run routine movie night.”
- “Open Wi-Fi settings,” “show device info,” “go back,” or “scroll down.” Navigation commands require the accessibility service.

Examples requiring ChatGPT:

- “I want to watch America’s Test Kitchen’s latest video on YouTube.”
- “Find the HBO series The Wire, open its page in Stremio, and show me season one.”
- “Find a good science article in the internal browser, read it, and summarize it.”
- “Save a movie-night routine that opens Stremio and sets the media volume to 30 percent.”
- “Remember that I prefer Stremio for shows and Silk for ordinary links.”

The AI can ask follow-up questions and retain the last few exchanges in memory. Connected mode always uses AI, including short app/show requests. **History → Use AI for current command** remains available; turn off local-only mode before using it. **Clear** starts a fresh conversation. Twenty recent commands and responses are stored locally and can be cleared in Settings.

## Tools

The app exposes **40 validated functions**, several supporting multiple actions:

| Area | Tools and operations |
| --- | --- |
| Local plans | Batch up to eight allowlisted tools with result references and parameters; resolve fresh scoped labels; reuse verified workflows, including parameterized public searches, and stop on drift |
| Apps and links | List installed apps, launch an app, search Stremio/YouTube/Netflix or a supported Android search activity, open HTTPS links in Silk/system/internal browser, web/video search |
| YouTube | Search public channel/video metadata, read newest uploads from a channel feed, request playback of an exact video, and check matching player-session evidence when available |
| Media | Search public Cinemeta movie/series metadata, open Stremio title or verified episode pages without autoplay, list active sessions, play/pause/stop/next/previous/seek/rewind/fast-forward when supported |
| TV | Read/change/mute Android media volume; open general/network/Bluetooth/display/sound/app settings; read device/connectivity information |
| Screen vision | Optional user-approved screen session, screenshot inspection, taps and swipes using image coordinates, bounded batches of visible keyboard keys; local history retains only the newest screenshot; WebSocket chains reset after at most three images |
| Native navigation | Read visible screen labels, click identified nodes, type in non-password fields, scroll, move focus/select, supported Back/Home/Recents/notification actions |
| Browsing | Read/click/type in the internal browser; web_page inspects public pages in a background WebView, triggers dynamic searches, and returns verified links for Silk |
| Personal utilities | Clock, arithmetic, current weather/three-day forecast, local notes/preferences, saved routines, in-process timers, optional TV text-to-speech, bounded waits |

Tool arguments, including nested keyboard arrays, are checked against their definitions before execution. All plan literals and supplied parameters are checked before the first step; result references are checked again once resolved. A failed text verification stops dependent plan steps. Each action reports what Android accepted. A launch or transport request does not prove that a page loaded or playback started; the AI is instructed to inspect results before claiming completion. Missing permissions and unsupported app actions return explicit errors.

## Usage control

Every command in connected mode consumes ChatGPT inference, including simple launches and utilities. Tool execution itself does not call OpenAI. Disconnected/local-only commands consume no ChatGPT inference; metadata and weather can still make public network requests.

The defaults are **4 AI requests and 10 tool steps per command**. Settings allows 1/2/4/6/8/12 AI requests and 4/8/10/16 tool steps. Nested routine and plan steps, including local scroll actions, count toward the tool budget. When two or more AI requests are configured, the last request is reserved for a factual completion/progress response. Responses are streamed with `store:false`; failed, incomplete, interrupted, and usage-limit streams are not accepted as successful completions. There is no automatic billing fallback, hidden retry loop, or unlimited background agent.

Version 0.2.0 opens one authenticated Responses WebSocket per task and sends only new tool results on continuations. Responses stay `store:false`. Before any generation is submitted, an unavailable WebSocket falls back to HTTPS/SSE; Android 6 uses HTTPS because the socket library's hostname verification requires API 24. A connection lost after submission stops the task instead of replaying uncertain work. Screenshots accumulate only within a bounded chain; after three images it starts a fresh chain on the same socket using local context with the latest image.

`action_plan` executes bounded JSON steps rather than arbitrary code. `ui_target` resolves a unique visible label in the expected package or HTTPS origin, with bounded polling/scrolling. Successful scoped click/wait plans and public-page searches with `$param` typing can be cached under `cache_name` only if their final label or result text is verified. `workflow` lists/runs/removes those paths. Cached paths re-resolve each label; they never reuse snapshots, coordinates or saved parameter values. Public search typing and its final verification label are stored only as parameter placeholders; every run checks the actual HTTPS origin and resolves fresh, unambiguous controls. The AI still interprets every connected command and chooses whether a path is relevant. The `public` scope targets the isolated page without requiring accessibility. `match: "text"` is reserved for public wait steps and checks visible body text, excluding input values. Use a result heading that includes the query or an exact result label. These tools cannot make a custom-rendered interface expose labels.

Verified public search plans now learn automatically when `cache_name` is omitted. Eligible routines have an optional click, one type action, and a final body-text wait on the same HTTPS origin. The verified text must contain the query exactly once. Learning replaces both the query and verification label with parameters and saves only control labels plus the surrounding verification wording. It rejects failures, unrelated headings, mixed origins, and controls containing the query. A `kind: public_search` workflow needs only `{"query":"new title"}` on replay; its verification is derived automatically, even if a caller supplies a different result label. The cache holds at most 20 workflows. Opening a site returns relevant workflows to the AI, which chooses a routine and then selects the correct item from fresh results. Learning does not save show/episode destinations or automate signed-in Silk pages. This avoids rebuilding familiar search plans; live latency improvements still require measurement.

Task timing is shown in the action log and saved as aggregate `last_performance` numbers: elapsed/model/action milliseconds, request/tool/batch counts, cache hits, transport and image-chain resets. No screenshots or tokens are stored with those metrics. Faster real browsing has not yet been measured for this version.

Models are discovered from the signed-in account. The initial automatic choice prefers an available Luna model; you can choose another eligible model in Settings. The app does not possess a private API for reading remaining subscription percentages. **Manage ChatGPT usage** opens the official usage page. Plus plan usage is shared across participating apps; the app itself cannot impose a percentage cap on the server-side allowance.

## Screen vision and browser control

**Settings → Enable screen vision** starts Android’s screen-capture consent flow. Choose Continue, then accept the OS prompt on the TV. The session has a foreground notification and can be stopped in Settings. After a process restart or upgrade, enable it again. The app captures up to 1280 pixels on the long side and sends a JPEG to your selected model only for requested visual observations during a task (`screen_see`, or an observation after `screen_tap`/`screen_swipe`/`keyboard_keys`). Screenshots stay in memory; they are not stored in history or logs. The capture surface remains active during the approved session, and only the newest raw frame is retained, allowing capture of static screens without encoding every frame. Screenshots can include visible account/page details. Password-entry screens with accessible password nodes are excluded; custom-rendered screens may not expose those nodes. Protected screens may appear black. The selected ChatGPT model must support image inputs.

The AI can use `screen_tap` and `screen_swipe` with a fresh screenshot ID. Coordinates are scaled to the physical display; out-of-bounds, reused, expired, or changed-app snapshots are rejected. Visual actions return a fresh screenshot and native node snapshot when available, reducing separate observation requests. The model must inspect that result: gesture delivery alone does not confirm content success. Native coordinate actions require Android 7/API 24 or newer and navigation permission. Screen vision is optional; Android 6/API 23 can still use supported non-gesture tools. Visual tasks can need more model requests; the default remains four to control Plus usage.

The internal browser already supports DOM reading, clicks, text entry, scrolling, and navigation. Those tools are available without full-screen capture. For Silk or custom-rendered native screens, screen vision supplies visual observation and coordinate actions. This implements our own UI tools through function calling; OpenAI’s hosted `computer` tool is not used on the current ChatGPT plan-sharing route.

## Permissions on this Fire TV

The user explicitly authorized navigation and playback access. Existing accessibility services were preserved. Fire OS exposes a placeholder for the standard accessibility settings intent and no standard notification-access settings activity on this device, so ADB setup is provided.

```sh
adb -P 5038 connect 192.168.1.104:5555
python3 install.py --device 192.168.1.104:5555 --enable-navigation --enable-playback
```

The permission flags are opt-in. The script preserves other accessibility services and rebinds this app's service after an APK update. Use `--disable-navigation` and/or `--disable-playback` to revoke those permissions. The app also exposes standard permission settings for devices that support them. ADB debugging can be turned off after setup; the app does not use ADB at runtime.

Use `--no-launch` with the installer to update without changing the foreground app.

Instrumentation testing stops/restarts the target process and can leave Fire OS's accessibility binding disconnected. Run the installer with the navigation flag after device tests or an upgrade to rebind it. Do not run instrumentation while completing sign-in or an AI task.

## Privacy and boundaries

The app requests internet, connection-state, media-volume, and optional microphone permissions;
it does not request storage permission. Keyboard dictation remains the command-input path and uses
the TV keyboard's own voice processing. RECORD_AUDIO is requested at runtime only after you explicitly
start **Mic test**; that local diagnostic retains only aggregate levels, never audio files or transcripts.

OAuth tokens are encrypted with AES-GCM using Android Keystore, stored only in app-private preferences, and excluded from backups. The implementation validates PKCE/state, RS256 signatures/JWKS, issuer/audience/nonce/expiry, granted plan scope, and returning account identity; refresh is serialized and saves rotated tokens together. Each account keeps its own client ID and token set. Sign-out attempts remote revocation before clearing local credentials. No Codex desktop credentials are copied.

Commands, history, notes, and preferences remain in the app's local sandbox. The current request, relevant tool results, installed app inventory, and explicit preferences are sent to OpenAI during an AI task. Website and app text are treated as untrusted content. Password entry is excluded. Sensitive labeled actions such as purchases, deletion, permission grants, and external sends require a visible confirmation. These checks reduce accidental actions; they are not a guarantee for every unlabeled or custom app UI.

## Compatibility and limits

- Requires Android API **23+**, internet for AI/catalog/weather, and an installed browser for OAuth. Fire OS and Android TV are supported by the native Android architecture; **only the specific Fire TV below has been tested**.
- **Vega-based Fire TVs are not Android devices and cannot run this APK.** They need a separate client.
- Dictation still requires the assistant's system keyboard. The optional Alexa-button accessibility
  redirect dismisses Alexa's visible UI and opens TV Assistant; it may flash Alexa briefly and is
  not a privileged button/microphone replacement. Raw remote capture and independent speech-to-text
  are unverified. Mic test is foreground-only and never adds background recording.
- Each app must support its link/search/session interface or expose readable accessibility nodes. Secure/custom-rendered/DRM interfaces may not be automatable. YouTube on this Fire TV exposes no useful screen labels; exact video links work, but its profile chooser may require you to choose a profile yourself. The assistant can use optional screen vision to inspect and tap these interfaces. Without screen vision, it reports an unreadable screen as a limitation. YouTube metadata comes from public web pages; site changes or consent pages can block lookup. **The public channel upload feed that `youtube_latest` depended on has been retired upstream by YouTube and now returns HTTP 404 for every channel, including deliberately invalid IDs.** `youtube_latest` therefore reports that limitation and points to `youtube_search` or reading the channel's Videos page, rather than retrying or inferring "latest" from search ranking. Verified in `VALIDATION.md`. There is no privileged shell, root, arbitrary JavaScript tool, universal app API, or silent app installation.
- Browser DOM tools use this app's WebView cookie store. The public web_page tool can inspect/search a public site in a background view, then AI opens a returned link in Silk. It cannot access Silk DOM/cookies or its signed-in state. Native keyboard input remains inconsistent for some dynamic Silk searches; signed-in pages still need native/visual navigation. The public-page view is bounded to one instance and destroyed with the assistant activity. `web_page` can participate in `action_plan`; opening a site returns up to five recently verified workflows for its origin, avoiding another lookup request. `wait_text` waits for expected visible results within a bounded deadline. Default waits observe content changes/stability instead of fixed sleeps; `settled` alone does not establish task success. Public label typing verifies the field immediately so a following batch wait can watch for AJAX results without another AI round trip. Oversized plan results preserve every step status and the final observation, avoiding a redundant read and retaining stopped-step errors.
- Software volume may be fixed on HDMI devices. Changing an external television/receiver's IR/CEC volume is not implemented.
- Timers run in this app's process; they are not persistent wake-up alarms and do not survive process death or wake a sleeping TV. Spoken output depends on an installed TTS engine and the Settings toggle.
- This is a sideloaded development release, targeting API 28 for the tested Fire OS. Store distribution and additional device/OS testing remain separate work. The source is provided under MIT; ChatGPT plan access remains subject to OpenAI's current account eligibility and preview behavior.

## Build and tests

Requirements: Android SDK platform 36, build-tools 36.0.0, a JDK, Python 3, `adb`, and `rg` for test-result checking. Set `ANDROID_SDK_ROOT` for a nondefault SDK location.

```sh
bash build.sh
bash test-local.sh  # offline JVM checks; never contacts the TV
# The signed APK is written to dist/tv-assistant.apk
TV_DEVICE=192.168.1.104:5555 TV_ADB_PORT=5038 bash test.sh
# Focused metadata and read-only live AI test; does not open a video:
TV_TEST_GROUP=youtube TV_DEVICE=192.168.1.104:5555 TV_ADB_PORT=5038 bash test.sh
```

The build downloads checksum-pinned Java-WebSocket/SLF4J jars from Maven Central on first use. See THIRD_PARTY_NOTICES.md. Offline checks use a separate org.json jar; it is not included in the APK.

Build intermediates and the development signing key are in the project's ignored `build/` directory. Preserve that key for updates to the installed development app. A distributable production release should use a separately managed release key.

Source layout: `MainActivity` owns the TV UI; `LocalCommands`/`LocalFormatter` handle direct commands; `AssistantEngine` runs bounded Responses function calls; `Tools` contains schemas and execution; `NavigationService` reads/acts on native nodes; `BrowserActivity` supplies restricted DOM operations; `ChatAuth`/`Vault` handle OAuth and encrypted accounts; `Net` handles HTTPS and event streams; `MicrophoneProbe`/`MicrophoneLevels` provide the local-only mic diagnostic. The device test runner covers parsing, argument rejection, identity signatures, completion/failure handling, tool budgets, and real WebView actions.

## Emulator development

A dedicated Android TV API 34 ARM64 AVD can test the app without using the physical Fire TV:

```sh
python3 emulator.py start             # visible TV window, host audio disabled
python3 emulator.py test --headless   # builds/installs/runs both emulator suites
python3 emulator.py stop              # stops only this project's emulator
```

The helper downloads the SDK image if needed and stores the AVD under ignored `build/avd/`.
It uses `TVAssistant_API34`, serial `emulator-5560`, and ADB port 5037. Every install/open/test/stop
checks emulator identity. It never selects an IP-address device, copies a ChatGPT account, records
host audio, or starts a live inference benchmark. The full suite makes read-only public YouTube
metadata requests; it uses no ChatGPT inference. Native tests exercise real accessibility controls,
text updates, verified workflow reuse, normal Android screen-capture consent, JPEG acquisition and
gesture delivery. Test reports are separate from Fire TV reports. Instrumentation cleanup rebinds
only our navigation service and removes the test APK.

Silk, Alexa, the Fire remote microphone, device-specific media/DRM behavior and live authenticated
AI performance still need their own validation. The emulator has no signed-in ChatGPT account.

## Verified device

Amazon **AFTKA**, Fire TV Stick 4K Max first generation, Fire OS **7.7.1.3**, Android 9/API 28. Amazon's `FireTVIME` delivered actual remote dictation into the earlier probe. The assistant's typed command/submission, calculator, app inventory, native screen reading, Stremio search, active playback-session access, and real internal browser DOM operations were exercised on-device. The test report is `reports/tv-assistant-test-results.txt`.

The user completed ChatGPT authorization on the TV. Version 0.2.1 passed 94 Fire TV checks. Live WebSocket continuation and AI batching completed the device/clock/calculator benchmark in 9.6 seconds versus a prior 18.0-second sample. AI opened and verified The Wire in Stremio, recovering from a catalog timeout. The full Silk site-search-to-The Wire Season 1 Episode 1 command completed in 31.8 seconds/seven requests, with the actual episode page verified. See `VALIDATION.md` for the current test count, latest live verification, and compatibility limits.

## Official integration references

- [OpenAI registration/sign-in](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)
- [OpenAI account/session lifecycle and Plus usage](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)
- [OpenAI model discovery and inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
- [Android accessibility services](https://developer.android.com/guide/topics/ui/accessibility/service)
- [Stremio deep links](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/deep-links.md)

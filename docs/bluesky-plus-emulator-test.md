# Bluesky+ Android emulator check

Tested 2026-11-09 on local `webslicer_android35`, Android 15/API 35,
device `emulator-5554`. Source branch: `bluesky-plus-android` (working tree).

Current result: implicit HTTPS profile and post links open the correct native
screens in Bluesky+. The repeated post-link emulator crashes were resolved
locally by switching from software rendering to the host GPU. See
[renderer diagnosis](#renderer-diagnosis-and-verified-workaround) below.

## Verified

- Generated the Android project with the fork variant and production service
  configuration using `pnpm prebuild --platform android --no-install`.
- Generated and merged manifests contain package `am.noz.blueskyplus`, the
  `blueskyplus` scheme, and the HTTPS `bsky.app` intent filter.
- Built an x86_64 debug APK from source with Gradle `:app:assembleDebug`.
  React Native, Hermes and the native modules compiled successfully; final
  build took 5m12s, with 1103 actionable tasks. This is a development build
  signed with the generated debug key, not a publisher-signed release.
- Installed the APK through the device tool and opened the fork against Metro
  on port 8081. The app reached the signed-out landing screen with Create
  account and Sign in controls. The Expo development menu identifies it as
  Bluesky+, runtime 1.138.0.
- The Android JavaScript bundle compiled successfully.

APK: `android/app/build/outputs/apk/debug/app-debug.apk` (generated, ignored by
Git). SHA-256: `bc341c290949af670b862f9f4760594556508caf14feabceafaa2f29717110b7`.
The development build requires Metro started with
`EXPO_PUBLIC_APP_VARIANT=bluesky-plus` and `EXPO_PUBLIC_ENV=production`.

## Build fix discovered during testing

The first build failed to resolve `io.bitdrift:capture:0.17.26`. Disabling the
Expo plugin had removed its repository setup, but native auto-linking still
included the SDK. Added `react-native.config.js` to exclude Bitdrift's Android
module only for the fork variant. The regenerated Android dependency graph and
package list exclude Bitdrift, and the original APK build now succeeds.

The focused app-config suite passes all 4 tests, including the new regression
check. All-platform `pnpm typecheck` and `pnpm lint` also pass.

## Signed-in follow-up

After the user signed in, the Settings screen was opened using
`blueskyplus://settings`. The new Open Bluesky links in Bluesky+ row and its
instructions rendered correctly. Pressing it successfully opened Android's
Open by default screen for Bluesky+. This verifies the application's actual
settings intent and package targeting.

The Open supported links toggle was enabled, with 0 verified links. Pressing
Add link opened a dialog listing exactly one supported domain, `bsky.app`,
initially unchecked. This confirms that the installed manifest advertises the
domain for user selection without needing automatic verification.

The controller continued to identify its session app as `am.noz.blueskyplus`
while Android Settings was foregrounded. It reported:

```text
press @e70 left am.noz.blueskyplus and foregrounded com.android.settings.
The tap likely escaped the app.
```

The screenshot confirmed that the first reported failure had actually opened
the expected Add link dialog. Following its advice, captured a new screenshot
and fresh snapshot. The subsequent checkbox tap produced the same foreground
guard error (`mw9gwkva-210069bf`), so device interaction stopped again under the
tool's retry rule. The checkbox tap might have been dispatched, but its result
and the final Add confirmation were not verified. The earlier Add link
diagnostic was `mw9gvo65-e9f4b967`.

## Earlier direct Settings launch failure

The device automation command could not open Android Settings. It returned:

```text
Failed to probe Android app sandbox for com.android.settings:
run-as: package not an application: com.android.settings
```

Confirmed that ADB reports `device` and the device tool lists the Settings
package. Retrying with the explicit Settings activity produced the same error.
Stopped device interaction after that retry, as required by the device-tool
instructions. Diagnostic IDs: `mw9fcsfu-71f35a45` and `mw9fdi8w-0d99f4ee`.

## Profile routing after manual domain selection

The user completed the Add confirmation in Android Settings. Opened
`https://bsky.app/profile/bsky.app` using the device command with the URL alone,
without passing an application package. Android selected `am.noz.blueskyplus`.
The accessibility snapshot and screenshot showed the correct Bluesky profile,
`@bsky.app`, including its profile header and posts in the native app. This
verifies the user-selected domain association, implicit HTTPS dispatch, and
profile navigation for the signed-in development build on Android 15.

## Earlier post-link test blocked by emulator crash

Opened this public post URL using the URL alone:

```text
https://bsky.app/profile/did:plc:p2dcugajbtsn44h5n2kgt7ox/post/3mztj24pp222r
```

The command reported the URL opened, but the next snapshot failed with
`adb: device 'emulator-5554' not found`. Device discovery confirmed the emulator
was no longer booted. Followed the device tool's advice to rediscover and reopen
it once. Metro had also stopped; restarted it with the fork environment and
two workers, then reconnected the development client. The signed-in session
persisted after the emulator restart.

The second post-link attempt produced the same missing-device failure. Stopped
device interaction under the tool's retry rule. Read-only host diagnostics
establish that the emulator host process actually crashed on both attempts:

- User journal: QEMU processes 3116627 and 3479615 dumped core at 17:23:10 and
  17:25:02 CET on 2026-11-09.
- `coredumpctl info 3479615`: `qemu-system-x86`, signal 11 (SEGV), executable
  `qemu-system-x86_64-headless`. The second core's process timestamp was
  17:24:51 CET; the journal entry was emitted after core processing.
- Second snapshot diagnostic: `mw9h26dg-7443a2f9`.

At this stage, the logs established an emulator-process crash without its cause,
so post navigation was left unverified. No Android app exception was established
by these host logs. The later renderer diagnosis below resolves this blocker.

The official app was not installed, so coexistence and competing domain
associations remain untested. Native sharing also remains untested. These remain
open in Taproot.

## Additional requested retry

On the user's next request, reconnected the running emulator and confirmed
Metro was available. Started a filtered logcat capture for Android runtime,
native fatal errors and React Native JavaScript errors. The signed-in app
loaded its feed normally.

Retried the original post URL. A device screenshot failed and discovery showed
the emulator had stopped. Host core diagnostics confirm another QEMU SIGSEGV,
PID 3510839, at 17:29:28 CET (journal entry at 17:29:37).

Following the tool's advice, reopened the same device once and reconnected the
app. Tested a different text-only post with no replies at lookup time:

```text
https://bsky.app/profile/did:plc:ewvi7nxzyoun6zhxrhs64oiz/post/3mzvdvyuzrl2d
```

That URL also reported opened, followed by a screenshot failure and another
QEMU core dump (PID 3526615, journal entry at 17:31:31 CET). No post-screen
snapshot was requested in either attempt this turn, so requesting the
accessibility tree is not necessary to reproduce the failure. The filtered
runtime log captured no entries; it does not establish absence of an Android
app error. Stopped device interaction after the failed retry.

The failure occurred with more than one post. At this stage its underlying
cause was unknown. No application source changes were made during these retries.

## Renderer diagnosis and verified workaround

Inspected the last host core with GDB (`coredumpctl debug 3526615`). The faulting
thread was executing generated SIMD instructions at `0x595607795b8b`, outside a
named shared-library symbol. Its stack contained return addresses into
`emulator/lib64/gles_swiftshader/libGLESv2.so`, including `0x748c7ccd4def` and
`0x748c7cd5c1ab`. Symbols were stripped, so the exact failing shader or function
was not identified. This strongly implicated the legacy software GLES renderer.
The selected emulator mode was `lavapipe`; that mode still loaded the legacy
SwiftShader GLES library alongside Lavapipe's Vulkan implementation.

Changed only the host emulator renderer. Created the previously absent local
file `/home/david/.android/advancedFeatures.ini` with:

```ini
ForceGpuHost = on
```

Booted the same AVD through the managed device tool. Generated
`hardware-qemu.ini` now reports `hw.gpu.mode = host`; Android SurfaceFlinger
reports `Android Emulator OpenGL ES Translator (AMD Radeon RX 9070 ...)`,
OpenGL ES 3.1, Mesa 26.0.8. The live QEMU process no longer maps the legacy
SwiftShader GLES library. The APK, Metro configuration, app source and stored
session were unchanged.

Retested both exact URLs above using URL-only dispatch, without supplying an
application package:

1. The minimized AT Protocol Developers post rendered its expected text,
   author, actions and reply field. The snapshot reports
   `App: am.noz.blueskyplus` and `postThreadItem-by-atproto.com`.
2. The original maura quint post rendered its expected text, author and thread,
   with 214 replies at test time. Screenshot and snapshot confirmed the content.
3. Reopened the AT Protocol Developers URL. A snapshot assertion checked both
   the selected application and expected post node. It passed, and the same
   QEMU process (PID 3597496) remained alive across all three launches.

Example repeated launch and verification:

```bash
/home/david/.t3/userdata/device/targets/t3-bed4d0c67c101b77c23e63b8/agent-device open 'https://bsky.app/profile/did:plc:ewvi7nxzyoun6zhxrhs64oiz/post/3mzvdvyuzrl2d'
/home/david/.t3/userdata/device/targets/t3-bed4d0c67c101b77c23e63b8/agent-device snapshot -i
```

```text
PASS: implicit HTTPS dispatch selected Bluesky+ and the expected post rendered.
PASS: emulator process still alive.
```

The earlier software-rendering configuration crashed four times across two
posts; the host-rendering configuration passed three launches across the same
two posts. This is a verified local workaround for an emulator graphics failure.
It does not identify or repair the underlying SwiftShader implementation bug.
There is no meaningful app-unit-test seam for a host QEMU rendering crash; the
device link-launch and rendered-content checks are the regression validation.
No application-code workaround or temporary app logging was added.

The override is local to this Linux user's emulator configuration and can affect
other AVDs started under that user. It is not part of the APK or Git changes.
To revert, remove `ForceGpuHost = on` (or remove this newly created file if it
still contains only this setting and its comment), then restart the emulator.
The previous configuration is expected to reproduce the crash.

Android documents renderer changes as a response to graphics problems in its
[emulator troubleshooting guide](https://developer.android.com/studio/run/emulator-troubleshooting).
The installed emulator's `lib/advancedFeatures.ini` documents `ForceGpuHost` as
selecting host GPU rendering. That local setting was verified by the generated
AVD configuration and Android's reported GLES renderer after restart.

Remaining device coverage includes native sharing, widgets, chat/media flows,
coexistence with the official app, and a publisher-signed release. Successful
post-link routing here is scoped to the signed-in Android 15 development build.

## Witchsky theme support

Added Settings → Appearance → Themes for the fork. The actual signed-in account
initially returned no records, and the screen showed Default, an empty-state
message and Refresh themes. Tested the picker first with temporary local
response data, then removed that data and its instrumentation.

With explicit user approval, copied BlueAT and Coffee / Espresso from
`did:plc:l37td5yhxl2irrzrgvei4qay` into the signed-in account's public
`app.witchsky.theme.colors` collection using its authenticated PDS client.
Both create operations succeeded. The temporary import UI was then removed;
subsequent app startup fetched the actual copied records from the user's PDS.
The original authors' records were not modified.

Verified on the existing signed-in Android 15/API 35 emulator:

- Default remains selectable alongside the fetched themes and their variants.
- Load more fetched the final empty page and disappeared while retaining all
  loaded theme options.
- BlueAT changes the app background to blue, surfaces to cyan, accent controls
  to purple, and text to white. Status-bar icons remain visible.
- Coffee / Espresso's Dark variant uses its near-black canvas and cream text;
  its Light variant changes the canvas to cream and text to dark brown.
- Each variant inherits unspecified positive/warning/critical/favorite colors.
- A custom theme hides the default color-mode controls; selecting Default
  restores them without changing their stored values. Font controls remain.
- A custom selection survives app restart and development-client reconnect;
  the feed and navigation also receive its colors. Selection/remount and
  account-switch isolation are additionally covered by hook tests.
- Both requested records declare `mode: light` despite dark canvases in some
  options. Effective chrome appearance is derived from canvas luminance so the
  system status/navigation icons follow the displayed colors.

Native screenshots are in `.impeccable/review/phone-android-blueat.png`,
`phone-android-coffee-dark.png`, and `phone-android-coffee-light.png`. The public
source records are retained only as test fixtures in the feature directory.
See [the feature mapping and behavior](../src/features/witchskyThemes/README.md).

An independent native finish review of Default, BlueAT, Coffee Dark and Coffee
Light screenshots passed without actionable findings. Default was restored after
the checks.

Focused feature tests pass: 3 suites / 11 tests. All-platform typecheck and lint
pass. No new native module was required, so these checks used the existing debug
APK with the updated JavaScript bundle. A publisher-signed build and physical
Android hardware remain untested. Web/iOS pass typechecking, but were not
visually or functionally exercised for this feature. Theme-provided color pairs
can have low contrast; they are not automatically rewritten.

## Immediate theme-switch feedback

The reported lag was reproduced with the existing switch: a tap on Coffee Dark
followed by `snapshot -i` still showed Coffee Light selected after the device
command settled for approximately 570 ms. A later snapshot showed Dark selected.
The radio was bound directly to the persisted app-wide preference, so it waited
for the same update that changed the entire themed tree.

Added a local pending radio choice and a native indeterminate **Applying theme…**
modal. Persistence starts after the modal's `onShow` and a paint boundary; the
modal remains until React has committed and painted the changed tree. Repeated
taps and duplicate presentation callbacks cannot start concurrent writes.
Unmount/account changes cancel scheduled work; save failures close progress and
restore the currently applied choice with a translated retry message.

Recorded the original and updated Android 15 flows through the bound device CLI.
In the updated Coffee Dark → Light clip, sampled frame 50 shows the Light radio
selected and the progress modal while the original dark canvas is still present.
Frame 86 shows the cream Light canvas with progress still visible; frame 87 shows
progress dismissed. Frames were sampled at 10 fps; these positions establish
rendering order, not tap-to-feedback latency. The app-wide update still takes
seconds in this development build. This change addresses immediate feedback.

The progress capture is
`.impeccable/review/phone-android-applying-theme.png`. Temporary video analysis
artifacts are under `/tmp/witchsky-switch-diagnosis`; no app instrumentation or
artificial test delays were added. The ordering regression failed before the
change and passes afterward. The feature suite now contains 16 passing tests,
including modal presentation/commit ordering, duplicate taps/callbacks, Default,
unmount/account-change cancellation and storage failure/retry.

All-platform typecheck, lint and formatting pass. An independent native finish
review passed after inspecting the progress capture and exact post-commit and
post-dismissal frames. A settled Light capture confirms the existing animated
system-bar transition finishes with dark icons. The Default path also completed
in the emulator, and Coffee / Espresso Light (the selection at the start of this
follow-up) was restored afterward. Physical devices, other platform runtimes,
TalkBack behavior and enlarged system fonts remain outside this check.

## Grouped theme variants

Appearance now lists each Witchsky record once. For a selected record with more
than one valid option, a **Variant** segmented control shows its base and variant
names, matching the existing default appearance controls. Base Color therefore
appears as one theme with **Dim / Dark**, instead of two theme rows. Single-option
themes hide the variant control. Separate records are not merged by name.

Existing per-variant preference IDs are preserved. A cached variant missing from
a fetch stays within its parent group and does not produce a second theme row.
More than three variant buttons can scroll horizontally. The segmented control's
initial layout callback is ignored when it reports the already-selected option,
so simply opening Appearance does not apply a theme or open progress.

On the signed-in Android 15 emulator, verified the single Base Color row, its Dim
and Dark control states, palette changes in both directions, and the unchanged
Applying theme progress behavior. Single-variant Coffee & Code has no variant
selector. Screenshots: `.impeccable/review/phone-android-theme-groups.png`,
`phone-android-base-color-dim.png`, and `phone-android-base-color-dark.png`.

Five grouping tests cover record identity, restoring the selected variant,
duplicate pages, missing cached variants, and offline/default state. All feature
tests pass: 4 suites / 21 tests. All-platform typecheck and lint pass. Repository
records are unchanged by this UI update.

A cold app restart recovered Base Color's Dark variant within the single parent
row, with the Dark segment selected and no automatic theme application on layout.
The independent native finish review passed without actionable findings. Testing
was limited to the existing Android 15 phone emulator and two short labels;
long-label/large-font, many-variant overflow, tablet, TalkBack and other-platform
runtime checks remain unverified.

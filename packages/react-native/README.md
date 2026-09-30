# react-native-clone-guard

Detect Android app cloners (MultiBox, Parallel Space, Clone App, Dual App, Second Space, …) in **Expo** and **bare React Native** apps.

Cloners run your APK inside a virtual container and fake what your app asks through Java, and even through libc. This library also asks the kernel directly with a raw syscall and flags any disagreement. It runs in the main activity's `onCreate`, **before your JavaScript loads**.

📖 **How it works:** [Catching Android App Cloners by Asking the Kernel](https://github.com/eshginfarzali/android-clone-detection#readme)

- Nine layered checks (K1–K9), including a libc-vs-syscall comparison that caught a cloner every Java-level check missed
- Can block before the JS bundle runs, or just report
- Emulator signals are reported, never enforced
- No SDK key, no server, no network calls. MIT, ~7 kB
- 16 KB page-size aligned (Android 15+, Google Play requirement)

## Install

```sh
npx expo install react-native-clone-guard
```

Bare React Native: run `npx install-expo-modules` once, then `npm install react-native-clone-guard`.

> Needs a development build (`expo prebuild`, EAS Build, or bare RN). **Expo Go is not supported**, since it can't load custom native code.

## Configure (Expo)

```json
{
  "expo": {
    "plugins": [
      ["react-native-clone-guard", {
        "block": true,
        "message": "Please use the original app, not a cloned copy."
      }]
    ]
  }
}
```

| Option | Default | |
|---|---|---|
| `block` | `false` | Show a non-dismissable dialog and close the app when a clone is detected |
| `message` | English default | Text of the blocking dialog; the reason code is appended |
| `acceptedSignatures` | `[]` | SHA-256 signing certificate hashes. Enables K8 (repackaging). **List both your upload key and your Google Play app signing key**, or Play installs will be blocked |

Start with `block: false`, collect `getCloneStatus()` from real users, and turn blocking on once genuine installs come back clean.

<details>
<summary>Bare React Native: configure through AndroidManifest</summary>

```xml
<application>
  <meta-data android:name="dev.clonedetection.BLOCK" android:value="true" />
  <meta-data android:name="dev.clonedetection.MESSAGE" android:value="Please use the original app." />
  <meta-data android:name="dev.clonedetection.SIGNATURES" android:value="sha256a,sha256b" />
</application>
```
</details>

## Use

```ts
import { getCloneStatus } from "react-native-clone-guard";

const status = getCloneStatus(); // synchronous, computed at launch

if (status?.isCloned) {
  // e.g. refuse a sensitive action, or tell your backend
}

// Send it with sensitive requests so the server keeps evidence
await api.post("/attendance", { ...payload, cloneReason: status?.cloneReason ?? null });
```

`getCloneStatus()` returns `null` on iOS, web and Expo Go.

```ts
interface CloneStatus {
  cloneReason: "K1" | "K2" | "K3" | "K4" | "K5" | "K6" | "K7" | "K8" | "K9" | null;
  isCloned: boolean;
  emulatorSignals: string[];   // e.g. ["hardware:ranchu", "tags:dev-keys"]
  isEmulator: boolean;
  uid: number;
  nativeUid: boolean;          // uid read from the kernel
  dataDirOwnerUid: number;     // via libc stat(), -1 = unreadable
  dataDirOwnerUidRaw: number;  // via raw syscall
  signatureSha256: string | null;
}
```

## Reason codes

| Code | Check |
|---|---|
| K1 | A foreign `Instrumentation` launched the activity (stack trace at `onCreate`) |
| K2 | Running as a secondary user (Dual App, Second Space, work profile) |
| K3 / K4 | Process UID doesn't belong to this package |
| K5 | Data directory lives inside another app |
| K6 | Own `base.apk` is not mapped from `/data/app` |
| K7 | Kernel `getuid()` differs from `Process.myUid()`, meaning a hook |
| K8 | Signing certificate not in `acceptedSignatures` (opt-in) |
| K9 | Data directory owner differs between libc `stat()` and a raw syscall |

## Limits

This is client-side detection. A rooted device with a determined attacker can defeat it. What it does is move cheating from "install a free app from the Play Store" to "root the phone and write a custom hook". Pair it with server-side checks for anything that matters.

## License

MIT © Eshgin Farzaliyev

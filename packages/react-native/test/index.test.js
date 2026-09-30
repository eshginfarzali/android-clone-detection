const test = require("node:test");
const assert = require("node:assert/strict");
const path = require("node:path");

const entry = path.resolve(__dirname, "../build/index.js");

/** Loads build/index.js with expo-modules-core replaced by a stub returning `nativeModule`. */
function load(nativeModule) {
  const core = require.resolve("expo-modules-core");
  require.cache[core] = {
    id: core,
    filename: core,
    loaded: true,
    exports: { requireOptionalNativeModule: (name) => (name === "CloneGuard" ? nativeModule : null) },
  };
  delete require.cache[entry];
  return require(entry);
}

const sample = {
  cloneReason: "K9",
  isCloned: true,
  emulatorSignals: [],
  isEmulator: false,
  uid: 10765,
  nativeUid: true,
  dataDirOwnerUid: 10765,
  dataDirOwnerUidRaw: -1,
  signatureSha256: null,
};

test("returns null when the native module is missing (iOS, web, Expo Go)", () => {
  assert.equal(load(null).getCloneStatus(), null);
});

test("returns the native result as-is", () => {
  assert.deepEqual(load({ getStatus: () => sample }).getCloneStatus(), sample);
});

test("returns null before the activity has evaluated", () => {
  assert.equal(load({ getStatus: () => null }).getCloneStatus(), null);
});

test("never throws into app code when the native call fails", () => {
  const status = load({
    getStatus: () => {
      throw new Error("bridge down");
    },
  }).getCloneStatus();
  assert.equal(status, null);
});

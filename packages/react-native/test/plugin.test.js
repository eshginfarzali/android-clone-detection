const test = require("node:test");
const assert = require("node:assert/strict");
const { applyCloneGuardManifest, META } = require("../app.plugin");

const emptyManifest = () => ({
  manifest: { application: [{ $: { "android:name": ".MainApplication" } }] },
});

const metaData = (manifest) =>
  Object.fromEntries(
    (manifest.manifest.application[0]["meta-data"] ?? []).map((m) => [m.$["android:name"], m.$["android:value"]])
  );

test("defaults write nothing: report-only, K8 off, default message", () => {
  assert.deepEqual(metaData(applyCloneGuardManifest(emptyManifest())), {});
});

test("block: true writes BLOCK=true", () => {
  assert.equal(metaData(applyCloneGuardManifest(emptyManifest(), { block: true }))[META.block], "true");
});

test("signatures are trimmed, lowercased and comma-joined", () => {
  const manifest = applyCloneGuardManifest(emptyManifest(), { acceptedSignatures: [" ABC123 ", "Def456", ""] });
  assert.equal(metaData(manifest)[META.signatures], "abc123,def456");
});

test("custom message is written verbatim", () => {
  const manifest = applyCloneGuardManifest(emptyManifest(), { message: "Use the original app." });
  assert.equal(metaData(manifest)[META.message], "Use the original app.");
});

test("turning an option off removes the previously written entry", () => {
  const manifest = applyCloneGuardManifest(emptyManifest(), { block: true, message: "x", acceptedSignatures: ["a"] });
  const cleared = metaData(applyCloneGuardManifest(manifest, {}));
  assert.equal(cleared[META.block], undefined);
  assert.equal(cleared[META.message], undefined);
  assert.equal(cleared[META.signatures], undefined);
});

test("re-running the plugin does not duplicate entries", () => {
  const once = applyCloneGuardManifest(emptyManifest(), { block: true });
  const twice = applyCloneGuardManifest(once, { block: true });
  const entries = twice.manifest.application[0]["meta-data"].filter((m) => m.$["android:name"] === META.block);
  assert.equal(entries.length, 1);
});

// Copies the detector core from ../../clone-guard into this package's Android
// sources. The core has one home; the npm tarball carries a copy of it.
const fs = require("fs");
const path = require("path");

const core = path.resolve(__dirname, "../../../clone-guard/src");
const target = path.resolve(__dirname, "../android/src");

const copies = [
  "main/cpp",
  "main/java/dev/clonedetection/guard/CloneDetector.kt",
  "main/java/dev/clonedetection/guard/CloneRules.kt",
  "main/java/dev/clonedetection/guard/NativeProbe.kt",
  // Unit tests run through this module's Gradle build; not shipped to npm.
  "test/java/dev/clonedetection/guard/CloneRulesTest.kt",
];

for (const file of copies) {
  const source = path.join(core, file);
  if (!fs.existsSync(source)) {
    console.error(`sync-core: missing ${source}`);
    process.exit(1);
  }
  fs.cpSync(source, path.join(target, file), { recursive: true });
}

console.log("sync-core: copied detector core and tests into android/src");

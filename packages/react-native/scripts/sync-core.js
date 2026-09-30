// Copies the detector core from ../../clone-guard into this package's Android
// sources. The core has one home; the npm tarball carries a copy of it.
const fs = require("fs");
const path = require("path");

const core = path.resolve(__dirname, "../../../clone-guard/src/main");
const target = path.resolve(__dirname, "../android/src/main");

const copies = [
  ["cpp", "cpp"],
  ["java/dev/clonedetection/guard/CloneDetector.kt", "java/dev/clonedetection/guard/CloneDetector.kt"],
  ["java/dev/clonedetection/guard/NativeProbe.kt", "java/dev/clonedetection/guard/NativeProbe.kt"],
];

for (const [from, to] of copies) {
  const source = path.join(core, from);
  if (!fs.existsSync(source)) {
    console.error(`sync-core: missing ${source}`);
    process.exit(1);
  }
  fs.cpSync(source, path.join(target, to), { recursive: true });
}

console.log("sync-core: copied detector core into android/src/main");

// Resolve through `expo` in apps (keeps the version in step with the SDK); the
// direct package is the fallback for this repo's own tests.
const { withAndroidManifest, AndroidConfig } = (() => {
  try {
    return require("expo/config-plugins");
  } catch {
    return require("@expo/config-plugins");
  }
})();

const { addMetaDataItemToMainApplication, getMainApplicationOrThrow, removeMetaDataItemFromMainApplication } =
  AndroidConfig.Manifest;

const META = {
  block: "dev.clonedetection.BLOCK",
  signatures: "dev.clonedetection.SIGNATURES",
  message: "dev.clonedetection.MESSAGE",
};

/** Writes the plugin options into the manifest as <meta-data>; unset options are removed. */
function applyCloneGuardManifest(manifest, options = {}) {
  const { block = false, acceptedSignatures = [], message } = options;
  const app = getMainApplicationOrThrow(manifest);

  const set = (name, value) =>
    value === undefined || value === ""
      ? removeMetaDataItemFromMainApplication(app, name)
      : addMetaDataItemToMainApplication(app, name, String(value));

  set(META.block, block ? "true" : undefined);
  set(META.signatures, acceptedSignatures.map((s) => s.trim().toLowerCase()).filter(Boolean).join(","));
  set(META.message, message);

  return manifest;
}

/**
 * Expo config plugin.
 *
 *   ["react-native-clone-guard", {
 *     block: true,                         // close the app on detection (default false)
 *     acceptedSignatures: ["<sha256>"],    // enables K8; list upload AND Play signing keys
 *     message: "Custom dialog text"
 *   }]
 */
function withCloneGuard(config, options = {}) {
  return withAndroidManifest(config, (config) => {
    config.modResults = applyCloneGuardManifest(config.modResults, options);
    return config;
  });
}

module.exports = withCloneGuard;
module.exports.applyCloneGuardManifest = applyCloneGuardManifest;
module.exports.META = META;

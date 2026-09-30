const { withAndroidManifest, AndroidConfig } = require("expo/config-plugins");

const { addMetaDataItemToMainApplication, getMainApplicationOrThrow, removeMetaDataItemFromMainApplication } =
  AndroidConfig.Manifest;

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
  const { block = false, acceptedSignatures = [], message } = options;

  return withAndroidManifest(config, (config) => {
    const app = getMainApplicationOrThrow(config.modResults);

    const set = (name, value) =>
      value === undefined || value === ""
        ? removeMetaDataItemFromMainApplication(app, name)
        : addMetaDataItemToMainApplication(app, name, String(value));

    set("dev.clonedetection.BLOCK", block ? "true" : undefined);
    set("dev.clonedetection.SIGNATURES", acceptedSignatures.map((s) => s.toLowerCase()).join(","));
    set("dev.clonedetection.MESSAGE", message);

    return config;
  });
}

module.exports = withCloneGuard;

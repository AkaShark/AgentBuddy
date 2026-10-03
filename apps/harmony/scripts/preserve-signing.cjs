// Capture DevEco's local signing configuration before staging source over the project.
// No key material or passwords are printed or placed in the repository.
const fs = require('fs');
const path = require('path');
const os = require('os');
const { execFileSync } = require('child_process');
const root = process.env.HARMONY_WORK_ROOT;
const json5 = require(path.join(process.env.DEVECO_ROOT,
  'tools/hvigor/hvigor-ohos-plugin/node_modules/json5'));
const profileFile = path.join(root, 'build/project/build-profile.json5');
if (fs.existsSync(profileFile)) {
  const profile = json5.parse(fs.readFileSync(profileFile, 'utf8'));
  const signing = profile.app?.signingConfigs?.find(config => config.material);
  if (signing) {
    // A staged release build must never replace the saved debug configuration.
    // Read only the public CMS profile; never print private signing material.
    const provision = JSON.parse(execFileSync('openssl', ['cms', '-verify', '-noverify',
      '-inform', 'DER', '-in', signing.material.profile], { stdio: ['ignore', 'pipe', 'pipe'] }));
    const directory = process.env.HARMONY_SIGNING_HOME || path.join(os.homedir(), '.agentBuddy/signing/harmony');
    fs.mkdirSync(directory, { recursive: true, mode: 0o700 });
    // DevEco encrypts passwords with the material directory next to the keystore.
    const material = path.join(path.dirname(signing.material.storeFile), 'material');
    const localMaterial = fs.existsSync(material) ? material : path.join(os.homedir(), '.ohos/config/material');
    if (fs.existsSync(localMaterial) && path.resolve(localMaterial) !== path.join(directory, 'material')) {
      fs.cpSync(localMaterial, path.join(directory, 'material'), { recursive: true });
    }
    // Keep a private copy of the certificate/profile as well as the config.
    for (const field of ['storeFile', 'certpath', 'profile']) {
      const original = signing.material[field];
      if (original && fs.existsSync(original)) {
        const destination = path.join(directory, path.basename(original));
        if (path.resolve(original) !== destination) fs.copyFileSync(original, destination);
        fs.chmodSync(destination, 0o600);
        signing.material[field] = destination;
      }
    }
    const destination = path.join(directory, provision.type === 'release' ?
      'release-signing-config.json' : 'signing-config.json');
    fs.writeFileSync(destination, JSON.stringify(signing, null, 2) + '\n', { mode: 0o600 });
    fs.chmodSync(destination, 0o600);
  }
}

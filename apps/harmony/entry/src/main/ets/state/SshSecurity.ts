import { TerminalSshTrustBackend, SshTrustLookup, SshCredentialProvider, SshCredentialRecord } from '@agentbuddy/core';
import { CredentialVault } from './CredentialVault';
import cryptoFramework from '@ohos.security.cryptoFramework';
import util from '@ohos.util';

// Synchronous callbacks required by Rust. All fields, including fingerprints,
// live in ASSET SECRET values; no passwords or private keys enter Preferences.
export class SshSecurity implements TerminalSshTrustBackend, SshCredentialProvider {
  constructor(private vault: CredentialVault) {}
  private key(kind: string, host: string, port: number): string {
    const digest = cryptoFramework.createMd('SHA256');
    digest.updateSync({ data: new util.TextEncoder().encodeInto(host.toLowerCase() + ':' + port) });
    const hash = Array.from(digest.digestSync().data).map(value => value.toString(16).padStart(2, '0')).join('');
    return 'agentbuddy.ssh.' + kind + '.' + hash;
  }

  read(host: string, port: number): SshTrustLookup {
    try {
      const fingerprint = this.vault.readSmallText(this.key('pin', host, port));
      return fingerprint ? { tag: 'Pinned', fingerprint } : { tag: 'NotPinned' };
    } catch (_) { return { tag: 'Unavailable', detail: '无法读取已保存的 SSH 主机密钥' }; }
  }

  write(host: string, port: number, fingerprint: string): void {
    this.vault.writeSmallText(this.key('pin', host, port), fingerprint);
  }

  remove(host: string, port: number): void {
    this.vault.removeSmall(this.key('pin', host, port));
  }

  loadCredential(host: string, port: number): SshCredentialRecord | undefined {
    // UniFFI's provider callback is infallible. Return no credential when ASSET
    // is unavailable so reconnect can report missing login instead of panicking.
    try { return this.readCredential(host, port); } catch (_) { return undefined; }
  }

  readCredential(host: string, port: number): SshCredentialRecord | undefined {
    const text = this.vault.readText(this.key('credential', host, port));
    return text ? JSON.parse(text) : undefined;
  }

  saveCredential(host: string, port: number, credential?: SshCredentialRecord): void {
    const key = this.key('credential', host, port);
    if (credential) this.vault.writeText(key, JSON.stringify(credential));
    else this.vault.removeSync(key);
  }
}

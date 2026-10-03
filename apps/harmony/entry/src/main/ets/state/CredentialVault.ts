import asset from '@ohos.security.asset';
import util from '@ohos.util';
import { SavedServerRecord } from '@agentbuddy/core';
import { SecretChunks, SecretBackend, SecretCodec } from './SecretChunks';

// Pair tokens and the iroh identity use the system credential store, never Preferences.
class AssetBackend implements SecretBackend {
  private encoder = new util.TextEncoder();

  read(key: string): Uint8Array | undefined {
    const query: asset.AssetMap = new Map();
    query.set(asset.Tag.ALIAS, this.encoder.encodeInto(key));
    query.set(asset.Tag.RETURN_TYPE, asset.ReturnType.ALL);
    try {
      const results = asset.querySync(query);
      return results.length ? results[0].get(asset.Tag.SECRET) as Uint8Array : undefined;
    } catch (error) {
      if ((error as Error & { code: number }).code === 24000002) return undefined;
      throw error;
    }
  }

  write(key: string, value: Uint8Array): void {
    const attributes: asset.AssetMap = new Map();
    attributes.set(asset.Tag.ALIAS, this.encoder.encodeInto(key));
    attributes.set(asset.Tag.SECRET, value);
    attributes.set(asset.Tag.ACCESSIBILITY, asset.Accessibility.DEVICE_FIRST_UNLOCKED);
    attributes.set(asset.Tag.CONFLICT_RESOLUTION, asset.ConflictResolution.OVERWRITE);
    asset.addSync(attributes);
  }

  remove(key: string): void {
    const query: asset.AssetMap = new Map();
    query.set(asset.Tag.ALIAS, this.encoder.encodeInto(key));
    try { asset.removeSync(query); } catch (error) {
      if ((error as Error & { code: number }).code !== 24000002) throw error;
    }
  }
}

class Utf8Codec implements SecretCodec {
  encode(text: string): Uint8Array { return new util.TextEncoder().encodeInto(text); }
  decode(bytes: Uint8Array): string { return new util.TextDecoder('utf-8').decodeToString(bytes); }
}

export class CredentialVault {
  private codec = new Utf8Codec();
  private backend = new AssetBackend();
  private chunks = new SecretChunks(this.backend, this.codec, () => util.generateRandomUUID());

  readSync(key: string): Uint8Array | undefined { return this.chunks.read(key); }
  writeSync(key: string, value: Uint8Array): void { this.chunks.write(key, value); }
  removeSync(key: string): void { this.chunks.remove(key); }
  async read(key: string): Promise<Uint8Array | undefined> { return this.readSync(key); }
  async write(key: string, value: Uint8Array): Promise<void> { this.writeSync(key, value); }
  readText(key: string): string | undefined {
    const bytes = this.readSync(key); return bytes ? this.codec.decode(bytes) : undefined;
  }
  writeText(key: string, value: string): void { this.writeSync(key, this.codec.encode(value)); }
  readSmallText(key: string): string | undefined {
    const bytes = this.backend.read(key); return bytes ? this.codec.decode(bytes) : undefined;
  }
  writeSmallText(key: string, value: string): void { this.backend.write(key, this.codec.encode(value)); }
  removeSmall(key: string): void { this.backend.remove(key); }

  async loadHosts(): Promise<SavedServerRecord[]> {
    const bytes = await this.read('agentbuddy.hosts.v1');
    if (!bytes) return [];
    const hosts: SavedServerRecord[] = JSON.parse(this.codec.decode(bytes));
    if (!Array.isArray(hosts)) throw new Error('保存的主机数据无法读取');
    return hosts;
  }

  async saveHosts(hosts: SavedServerRecord[]): Promise<void> {
    await this.write('agentbuddy.hosts.v1', this.codec.encode(JSON.stringify(hosts)));
  }
}

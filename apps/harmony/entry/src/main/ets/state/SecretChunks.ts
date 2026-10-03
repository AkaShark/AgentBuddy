export interface SecretBackend {
  read(key: string): Uint8Array | undefined;
  write(key: string, value: Uint8Array): void;
  remove(key: string): void;
}
export interface SecretCodec {
  encode(text: string): Uint8Array;
  decode(bytes: Uint8Array): string;
}
interface SecretManifest { generation: string; length: number; chunks: number; }

// ASSET limits each encrypted SECRET to 1024 bytes. Publish a new manifest only
// after every chunk exists, so interrupted writes retain the previous value.
// Manifests and chunks both go into encrypted SECRET fields, never asset labels.
export class SecretChunks {
  private backend: SecretBackend;
  private codec: SecretCodec;
  private generation: () => string;
  constructor(backend: SecretBackend, codec: SecretCodec, generation: () => string) {
    this.backend = backend; this.codec = codec; this.generation = generation;
  }

  private manifest(key: string): SecretManifest | undefined {
    const bytes = this.backend.read(key + '.chunks.v1');
    if (!bytes) return undefined;
    const value: SecretManifest = JSON.parse(this.codec.decode(bytes));
    if (!/^[a-zA-Z0-9-]{1,64}$/.test(value.generation) || !Number.isInteger(value.length) || value.length < 0 ||
      value.length > 256 * 1024 || value.chunks !== Math.ceil(value.length / 1024)) throw new Error('安全存储索引损坏');
    return value;
  }

  read(key: string): Uint8Array | undefined {
    const manifest = this.manifest(key);
    if (!manifest) return this.backend.read(key); // Migrate existing paired hosts/identity on the next write.
    const bytes = new Uint8Array(manifest.length);
    for (let i = 0; i < manifest.chunks; i++) {
      const chunk = this.backend.read(`${key}.${manifest.generation}.${i}`);
      if (!chunk || chunk.length !== Math.min(1024, manifest.length - i * 1024)) throw new Error('安全存储内容不完整');
      bytes.set(chunk, i * 1024);
    }
    return bytes;
  }

  write(key: string, bytes: Uint8Array): void {
    if (bytes.length > 256 * 1024) throw new Error('安全存储内容过大');
    const previous = this.manifest(key);
    const manifest: SecretManifest = { generation: this.generation(), length: bytes.length, chunks: Math.ceil(bytes.length / 1024) };
    const written: string[] = [];
    try {
      for (let i = 0; i < manifest.chunks; i++) {
        const alias = `${key}.${manifest.generation}.${i}`;
        this.backend.write(alias, bytes.slice(i * 1024, (i + 1) * 1024)); written.push(alias);
      }
      this.backend.write(key + '.chunks.v1', this.codec.encode(JSON.stringify(manifest)));
    } catch (error) {
      for (const alias of written) { try { this.backend.remove(alias); } catch (_) {} }
      throw error;
    }
    this.removeGeneration(key, previous);
    try { this.backend.remove(key); } catch (_) {}
  }

  remove(key: string): void {
    const manifest = this.manifest(key);
    // Remove any legacy value first: deleting the manifest must not reveal it.
    this.backend.remove(key);
    this.backend.remove(key + '.chunks.v1');
    this.removeGeneration(key, manifest);
  }

  private removeGeneration(key: string, manifest?: SecretManifest): void {
    if (!manifest) return;
    for (let i = 0; i < manifest.chunks; i++) {
      try { this.backend.remove(`${key}.${manifest.generation}.${i}`); } catch (_) {}
    }
  }
}

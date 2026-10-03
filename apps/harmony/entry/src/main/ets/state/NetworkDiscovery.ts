import common from '@ohos.app.ability.common';
import mdns from '@ohos.net.mdns';
import connection from '@ohos.net.connection';
import { AppDiscoveredServer, AppDiscoverySource, AppMdnsSeed, DiscoveryBridge,
  DiscoveryScanSubscription, ProgressiveDiscoveryUpdateKind } from '@agentbuddy/core';

export class DiscoveryState {
  scanning: boolean = false;
  progress: number = 0;
  label: string = '';
  error: string = '';
  servers: AppDiscoveredServer[] = [];
}

// Like Android NetworkDiscovery: native mDNS/IP access only. Rust owns network
// probes, ranking, merge and deduplication; each emitted list is authoritative.
export class NetworkDiscovery {
  private state = new DiscoveryState();
  private generation: number = 0;
  private browsers: mdns.DiscoveryService[] = [];
  private finishBrowse?: () => void;
  private observer?: (state: DiscoveryState) => void;

  observe(observer: (state: DiscoveryState) => void): void { this.observer = observer; this.emit(); }
  private emit(): void { this.observer?.(Object.assign(new DiscoveryState(), this.state)); }
  dispose(): void { this.stop(); this.observer = undefined; }

  stop(): void {
    this.generation++;
    this.finishBrowse?.(); this.finishBrowse = undefined;
    for (const browser of this.browsers) {
      try { browser.stopSearchingMDNS(); browser.off('serviceFound'); browser.off('serviceLost'); } catch (_) {}
    }
    this.browsers = [];
    this.state.scanning = false; this.emit();
  }

  async start(context: common.Context): Promise<void> {
    if (this.state.scanning) return;
    const generation = ++this.generation;
    this.state.scanning = true; this.state.error = ''; this.state.progress = 0;
    this.state.label = '正在发现局域网服务…'; this.emit();
    let bridge: DiscoveryBridge | undefined;
    let subscription: DiscoveryScanSubscription | undefined;
    try {
      const seeds = await this.mdnsSeeds(context, generation);
      const address = await this.localIpv4();
      if (generation !== this.generation) return;
      this.state.progress = 0.02; this.state.label = '正在探测可用主机…'; this.emit();
      bridge = new DiscoveryBridge();
      subscription = bridge.scanServersWithMdnsContextProgressive(seeds, address);
      while (generation === this.generation) {
        const update = await subscription.nextEvent();
        if (generation !== this.generation) return;
        this.state.servers = update.servers;
        this.state.progress = update.progress;
        this.state.label = update.kind === ProgressiveDiscoveryUpdateKind.ScanComplete
          ? '发现完成' : discoveryLabel(update.source);
        this.emit();
        if (update.kind === ProgressiveDiscoveryUpdateKind.ScanComplete) break;
      }
    } catch (error) {
      if (generation === this.generation) this.state.error = error instanceof Error ? error.message : '发现失败，可重试或手动添加。';
    } finally {
      subscription?.destroy(); bridge?.destroy();
      if (generation === this.generation) { this.state.scanning = false; this.emit(); }
    }
  }

  private async mdnsSeeds(context: common.Context, generation: number): Promise<AppMdnsSeed[]> {
    const found = new Map<string, mdns.LocalServiceInfo>();
    // Each generation owns its browsers. An old cancelled scan must not stop
    // the browsers of a new scan started before its promise resumes.
    const browsers: mdns.DiscoveryService[] = [];
    this.browsers = browsers;
    for (const type of ['_ssh._tcp', '_codex._tcp']) {
      try {
        const browser = mdns.createDiscoveryService(context, type);
        browser.on('serviceFound', (service: mdns.LocalServiceInfo) => {
          if (generation === this.generation) found.set(service.serviceType + ':' + service.serviceName, service);
        });
        browser.on('serviceLost', (service: mdns.LocalServiceInfo) => { found.delete(service.serviceType + ':' + service.serviceName); });
        browsers.push(browser); browser.startSearchingMDNS();
      } catch (_) { /* Rust probing and manual entry remain available without mDNS. */ }
    }
    await new Promise<void>(resolve => {
      const timer = setTimeout(() => {
        if (generation === this.generation) this.finishBrowse = undefined;
        resolve();
      }, 4000);
      this.finishBrowse = () => { clearTimeout(timer); resolve(); };
    });
    for (const browser of browsers) {
      try { browser.stopSearchingMDNS(); browser.off('serviceFound'); browser.off('serviceLost'); } catch (_) {}
    }
    if (this.browsers === browsers) this.browsers = [];
    if (generation !== this.generation) return [];
    const seeds: AppMdnsSeed[] = [];
    await Promise.all([...found.values()].map(async (service: mdns.LocalServiceInfo) => {
      let timer: number = -1;
      try {
        const resolved = await Promise.race([mdns.resolveLocalService(context, service),
          new Promise<undefined>(resolve => { timer = setTimeout(() => resolve(undefined), 2000); })]);
        if (resolved?.host?.address) seeds.push({ name: resolved.serviceName, host: resolved.host.address,
          port: resolved.port, serviceType: service.serviceType + '.' });
      } catch (_) {} finally { clearTimeout(timer); }
    }));
    return seeds;
  }

  private async localIpv4(): Promise<string | undefined> {
    try {
      for (const net of await connection.getAllNets()) {
        const capabilities = await connection.getNetCapabilities(net);
        if (!capabilities.bearerTypes.includes(connection.NetBearType.BEARER_WIFI)) continue;
        const properties = await connection.getConnectionProperties(net);
        return properties.linkAddresses.find(link => (link.address.family ?? 1) === 1)?.address.address;
      }
    } catch (_) {}
    return undefined;
  }
}

export function discoveryLabel(source?: AppDiscoverySource): string {
  switch (source) {
    case AppDiscoverySource.Bonjour: return '局域网服务';
    case AppDiscoverySource.Tailscale: return 'Tailscale 网络';
    case AppDiscoverySource.LanProbe: return '局域网探测';
    case AppDiscoverySource.ArpScan: return '邻近主机';
    default: return '正在探测可用主机…';
  }
}

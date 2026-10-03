import { AppServerHealth, AppServerSnapshot, AppDiscoverySource } from '@agentbuddy/core';

export function hostHealthLabel(server?: AppServerSnapshot): string {
  switch (server?.health) {
    case AppServerHealth.Connected: return '已连接';
    case AppServerHealth.Connecting: return '正在连接';
    case AppServerHealth.Unresponsive: return '连接暂未响应';
    case AppServerHealth.Unknown: return '正在检查连接';
    default: return '未连接';
  }
}

export function savedDiscoverySource(source: AppDiscoverySource): string {
  switch (source) {
    case AppDiscoverySource.Bonjour: return 'bonjour';
    case AppDiscoverySource.Tailscale: return 'tailscale';
    case AppDiscoverySource.LanProbe: return 'lan';
    case AppDiscoverySource.ArpScan: return 'arp';
    default: return 'manual';
  }
}

export interface MessagePresentation {
  title: string;
  text: string;
  code: boolean;
  role: string;
  images: string[];
  remoteImagePath?: string;
  renderRevision: number;
}

export function presentationRevision(previous: MessagePresentation | undefined, next: MessagePresentation): number {
  const same = previous && previous.title === next.title && previous.text === next.text &&
    previous.code === next.code && previous.role === next.role && previous.remoteImagePath === next.remoteImagePath &&
    previous.images.length === next.images.length &&
    previous.images.every((image, index) => image === next.images[index]);
  return (previous?.renderRevision ?? 0) + (same ? 0 : 1);
}

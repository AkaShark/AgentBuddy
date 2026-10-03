// Resolve at action time: prepending an older page changes the user index.
// Rust owns rollback depth and validates the canonical history boundary.
export interface SelectableMessage {
  id: string;
  content: { tag: string };
  isFromUserTurnBoundary: boolean;
}

export function loadedUserIndex(items: SelectableMessage[], id: string): number {
  let index = 0;
  for (const item of items) {
    if (item.content.tag !== 'User') continue;
    if (item.id === id) return item.isFromUserTurnBoundary ? index : -1;
    index++;
  }
  return -1;
}

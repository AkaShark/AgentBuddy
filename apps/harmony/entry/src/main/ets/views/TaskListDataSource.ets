import { HomeTaskItem, taskRenderKey } from '../state/HomeTaskList';

// Only visible rows are composed, even when Rust has loaded a long task history.
export class TaskListDataSource implements IDataSource {
  private items: HomeTaskItem[] = [];
  private listeners: DataChangeListener[] = [];
  totalCount(): number { return this.items.length; }
  getData(index: number): HomeTaskItem { return this.items[index]; }
  registerDataChangeListener(listener: DataChangeListener): void {
    if (!this.listeners.includes(listener)) this.listeners.push(listener);
  }
  unregisterDataChangeListener(listener: DataChangeListener): void {
    this.listeners = this.listeners.filter(value => value !== listener);
  }
  replace(items: HomeTaskItem[]): void {
    if (items.length === this.items.length && items.every((item, index) => taskRenderKey(item) === taskRenderKey(this.items[index]))) return;
    this.items = items;
    this.listeners.forEach(listener => listener.onDataReloaded());
  }
}

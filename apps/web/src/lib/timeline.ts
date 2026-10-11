import type { TimelineDiff, TimelineEntry } from "./types";

export function applyDiffs(list: TimelineEntry[], diffs: TimelineDiff[]): TimelineEntry[] {
  let next = list.slice();
  for (const diff of diffs) {
    switch (diff.op) {
      case "reset":
        next = diff.value.slice();
        break;
      case "append":
        next.push(...diff.value);
        break;
      case "clear":
        next = [];
        break;
      case "pushFront":
        next.unshift(diff.value);
        break;
      case "pushBack":
        next.push(diff.value);
        break;
      case "popFront":
        next.shift();
        break;
      case "popBack":
        next.pop();
        break;
      case "insert":
        next.splice(diff.value.index, 0, diff.value.entry);
        break;
      case "set":
        next[diff.value.index] = diff.value.entry;
        break;
      case "remove":
        next.splice(diff.value.index, 1);
        break;
      case "truncate":
        next.length = Math.min(next.length, diff.value.length);
        break;
    }
  }
  return next;
}

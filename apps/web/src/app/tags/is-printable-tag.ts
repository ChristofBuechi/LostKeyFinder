import { TagResponse } from '@lost-key-finder/api-client';

export function isPrintableTag(tag: TagResponse): boolean {
  return tag.origin === 'SELF_CREATED' && !tag.deletedAt && (tag.status === 'ACTIVE' || tag.status === 'INACTIVE');
}

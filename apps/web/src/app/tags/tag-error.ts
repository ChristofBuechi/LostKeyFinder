import { HttpErrorResponse } from '@angular/common/http';
import { TagCopy } from './tag-copy';

export function tagError(error: unknown, copy: ReturnType<TagCopy['text']>): string {
  if (!(error instanceof HttpErrorResponse)) return copy.failed;
  switch (error.status) {
    case 401: return copy.expired;
    case 403: return copy.denied;
    case 404: return copy.registrationMissing;
    case 409: return copy.conflict;
    case 429: return copy.rateLimit;
    default: return copy.failed;
  }
}

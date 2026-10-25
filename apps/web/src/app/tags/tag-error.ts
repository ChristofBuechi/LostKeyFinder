import { HttpErrorResponse } from '@angular/common/http';
import { TagCopy } from './tag-copy';

export function tagError(error: unknown, copy: ReturnType<TagCopy['text']>): string {
  if (!(error instanceof HttpErrorResponse)) return copy.failed;
  switch (error.status) {
    case 401: return copy.expired;
    case 403: return copy.denied;
    case 404: return copy.registrationMissing;
    case 409: {
      // CSV requests use responseType text, so their problem details arrive as JSON strings.
      let body: unknown = error.error;
      if (typeof body === 'string') {
        try { body = JSON.parse(body); } catch { return copy.conflict; }
      }
      return body && typeof body === 'object' && 'detail' in body && body.detail === 'Batch contains deleted tags'
        ? copy.csvDeleted : copy.conflict;
    }
    case 429: return copy.rateLimit;
    default: return copy.failed;
  }
}

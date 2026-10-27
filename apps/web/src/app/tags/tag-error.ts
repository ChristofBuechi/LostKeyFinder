import { HttpErrorResponse } from '@angular/common/http';
import { TranslationCatalog } from '../core/translations';

export function tagError(error: unknown, copy: TranslationCatalog['tags']): string {
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

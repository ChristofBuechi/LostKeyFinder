import { isUuidV4 } from '../core/uuid';
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { UsersService } from '@lost-key-finder/api-client';
import { Auth } from './auth';

export const authGuard: CanActivateFn = async route => {
  const auth = inject(Auth);
  const router = inject(Router);
  const users = inject(UsersService);
  const chatId = route.paramMap.get('chatId');
  const login = router.createUrlTree(['/'], { fragment: 'owner', queryParams:
    chatId && isUuidV4(chatId)
      ? { chatId: chatId.toLowerCase() } : undefined });
  await auth.ready;
  if (!auth.signedIn()) return login;
  const identity = auth.firebaseUserId();
  try {
    const user = await firstValueFrom(users.userControllerMe());
    return auth.signedIn() && identity === auth.firebaseUserId() && user.status === 'ACTIVE' && (!route.data['admin'] || user.isAdmin) ? true : login;
  } catch { return login; }
};

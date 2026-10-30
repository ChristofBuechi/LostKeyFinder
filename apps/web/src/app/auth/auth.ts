import { isUuidV4 } from '../core/uuid';
import { DestroyRef, Injectable, InjectionToken, inject, signal } from '@angular/core';
import type { FirebaseOptions } from 'firebase/app';
import { AuthCallbackData } from './auth-callback';
import { LanguageService } from '../core/language.service';

export type AuthConfiguration = Pick<FirebaseOptions, 'apiKey' | 'authDomain' | 'projectId' | 'appId'>;

export const AUTH_CONFIGURATION = new InjectionToken<AuthConfiguration>('Auth configuration', {
  providedIn: 'root', factory: () => ({}),
});
export const AUTH_CALLBACK = new InjectionToken<AuthCallbackData>('Consumed Auth callback', {
  providedIn: 'root', factory: () => ({ link: '', failed: true }),
});
export const AUTH_CLIENT = new InjectionToken('Firebase Auth client', {
  providedIn: 'root',
  factory: () => {
    const config = inject(AUTH_CONFIGURATION);
    return config.apiKey && config.authDomain && config.projectId && config.appId
      ? Promise.all([import('firebase/app'), import('firebase/auth')]).then(([app, sdk]) => {
        const auth = sdk.getAuth(app.getApps().length ? app.getApp() : app.initializeApp(config));
        return { auth, sdk };
      }) : Promise.resolve(null);
  },
});

const emailStorageKey = 'firebaseEmailForSignIn';

@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly client = inject(AUTH_CLIENT);
  private readonly language = inject(LanguageService);
  private readonly callback = inject(AUTH_CALLBACK);
  readonly registrationId = this.callback.registrationId;
  readonly finderReportId = this.callback.finderReportId;
  readonly chatId = this.callback.chatId;
  readonly firebaseUserId = signal<string | null>(null);
  private readonly signedInState = signal(false);
  readonly signedIn = this.signedInState.asReadonly();
  private readonly configuration = inject(AUTH_CONFIGURATION);
  readonly configured = !!(this.configuration.apiKey && this.configuration.authDomain &&
    this.configuration.projectId && this.configuration.appId);
  readonly needsEmail = signal(false);
  readonly ready: Promise<void>;

  constructor() {
    this.ready = this.initialize(inject(DestroyRef)).catch(() => this.signedInState.set(false));
  }

  private async initialize(destroyRef: DestroyRef): Promise<void> {
    const client = await this.client;
    if (!client || destroyRef.destroyed) return;
    const unsubscribe = client.sdk.onIdTokenChanged(client.auth, user => {
      this.firebaseUserId.set(user?.uid ?? null);
      this.signedInState.set(!!user);
    });
    destroyRef.onDestroy(unsubscribe);
    await client.auth.authStateReady();
    this.firebaseUserId.set(client.auth.currentUser?.uid ?? null);
    this.signedInState.set(!!client.auth.currentUser);
  }

  async sendLink(email: string, registrationId?: string, flow?: { finderReportId?: string; chatId?: string }): Promise<void> {
    const client = await this.client;
    if (!client) throw new Error('Auth unavailable');
    const continuation = new URL('/auth/callback', location.origin);
    if (registrationId) {
      if (!isUuidV4(registrationId)) throw new Error('Invalid registration');
      continuation.searchParams.set('registrationId', registrationId.toLowerCase());
    }
    for (const [name, value] of Object.entries(flow ?? {})) {
      if (!value || !isUuidV4(value)) throw new Error('Invalid continuation');
      continuation.searchParams.set(name, value.toLowerCase());
    }
    if (continuation.searchParams.size > 1) throw new Error('Invalid continuation');
    try {
      // Persist before sending so a successful link can always be completed here.
      localStorage.setItem(emailStorageKey, email.trim());
      client.auth.languageCode = this.language.language();
      await client.sdk.sendSignInLinkToEmail(client.auth, email.trim(), {
        url: continuation.href, handleCodeInApp: true,
      });
    } catch { throw new Error('Link delivery failed'); }
  }

  async completeCallback(email?: string): Promise<void> {
    await this.ready;
    const client = await this.client;
    const { link, failed } = this.callback;
    if (!client || failed || !client.sdk.isSignInWithEmailLink(client.auth, link)) throw new Error('Invalid callback');
    const address = email?.trim() || localStorage.getItem(emailStorageKey);
    if (!address) {
      this.needsEmail.set(true);
      return;
    }
    this.callback.link = '';
    this.callback.failed = true;
    try {
      const result = await client.sdk.signInWithEmailLink(client.auth, address, link);
      this.firebaseUserId.set(result.user.uid);
      localStorage.removeItem(emailStorageKey);
      this.needsEmail.set(false);
      this.signedInState.set(true);
    } catch { throw new Error('Invalid callback'); }
  }

  async accessToken(): Promise<string | null> {
    await this.ready;
    const client = await this.client;
    return client?.auth.currentUser?.getIdToken() ?? null;
  }

  expireSession(): void {
    this.firebaseUserId.set(null);
    this.signedInState.set(false);
    void this.client.then(client => client?.sdk.signOut(client.auth)).catch(() => { /* local data stays hidden even if SDK cleanup fails */ });
  }

  async signOut(): Promise<void> {
    const client = await this.client;
    if (!client) return;
    try { await client.sdk.signOut(client.auth); }
    catch { throw new Error('Logout failed'); }
    this.firebaseUserId.set(null);
    this.signedInState.set(false);
  }
}

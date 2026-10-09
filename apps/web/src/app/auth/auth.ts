import { DestroyRef, Injectable, InjectionToken, inject, signal } from '@angular/core';
import type { SupabaseClient } from '@supabase/supabase-js';
import { AuthCallbackData } from './auth-callback';

export interface AuthConfiguration {
  url: string;
  publishableKey: string;
}

export const AUTH_CONFIGURATION = new InjectionToken<AuthConfiguration>('Auth configuration', {
  providedIn: 'root', factory: () => ({ url: '', publishableKey: '' }),
});
export const AUTH_CALLBACK = new InjectionToken<AuthCallbackData>('Consumed Auth callback', {
  providedIn: 'root', factory: () => ({ code: '', failed: true }),
});
export const AUTH_CLIENT = new InjectionToken<Promise<SupabaseClient | null>>('Supabase Auth client', {
  providedIn: 'root',
  factory: () => {
    const config = inject(AUTH_CONFIGURATION);
    return config.url && config.publishableKey ? import('@supabase/supabase-js').then(({ createClient }) => createClient(config.url, config.publishableKey, {
      auth: { flowType: 'pkce', detectSessionInUrl: false, persistSession: true, autoRefreshToken: true },
    })) : Promise.resolve(null);
  },
});

@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly client = inject(AUTH_CLIENT);
  private readonly callback = inject(AUTH_CALLBACK);
  private readonly signedInState = signal(false);
  readonly signedIn = this.signedInState.asReadonly();
  readonly configured = !!inject(AUTH_CONFIGURATION).url && !!inject(AUTH_CONFIGURATION).publishableKey;
  readonly ready: Promise<void>;

  constructor() {
    this.ready = this.initialize(inject(DestroyRef)).catch(() => {
      this.signedInState.set(false);
    });
  }

  private async initialize(destroyRef: DestroyRef): Promise<void> {
    const client = await this.client;
    if (!client || destroyRef.destroyed) return;
    const subscription = client.auth.onAuthStateChange((_event, session) => {
      this.signedInState.set(!!session);
    }).data.subscription;
    destroyRef.onDestroy(() => subscription.unsubscribe());
    await this.accessToken();
  }

  async sendLink(email: string): Promise<void> {
    const client = await this.client;
    if (!client) throw new Error('Auth unavailable');
    const { error } = await client.auth.signInWithOtp({
      email: email.trim(),
      options: { shouldCreateUser: true, emailRedirectTo: `${location.origin}/auth/callback` },
    });
    if (error) throw new Error('Link delivery failed');
  }

  async completeCallback(): Promise<void> {
    const { code, failed } = this.callback;
    this.callback.code = '';
    this.callback.failed = true;
    if (failed || !code) throw new Error('Invalid callback');
    await this.ready;
    const client = await this.client;
    if (!client) throw new Error('Auth unavailable');
    const { data, error } = await client.auth.exchangeCodeForSession(code);
    if (error || !data.session) throw new Error('Invalid callback');
    this.signedInState.set(true);
  }

  async accessToken(): Promise<string | null> {
    const client = await this.client;
    if (!client) return null;
    const { data, error } = await client.auth.getSession();
    if (error) throw new Error('Session unavailable');
    this.signedInState.set(!!data.session);
    return data.session?.access_token ?? null;
  }

  async signOut(): Promise<void> {
    const client = await this.client;
    if (!client) return;
    const { error } = await client.auth.signOut({ scope: 'local' });
    if (error) throw new Error('Logout failed');
    this.signedInState.set(false);
  }
}

import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AUTH_CALLBACK, AUTH_CLIENT, Auth } from './auth';

describe('Owner Auth session lifecycle', () => {
  const session = { access_token: 'test-access-token' };
  let client: { auth: {
    getSession: ReturnType<typeof vi.fn>;
    onAuthStateChange: ReturnType<typeof vi.fn>;
    signInWithOtp: ReturnType<typeof vi.fn>;
    exchangeCodeForSession: ReturnType<typeof vi.fn>;
    signOut: ReturnType<typeof vi.fn>;
  } };

  beforeEach(() => {
    TestBed.resetTestingModule();
    client = { auth: {
      getSession: vi.fn().mockResolvedValue({ data: { session: null }, error: null }),
      onAuthStateChange: vi.fn().mockReturnValue({ data: { subscription: { unsubscribe: vi.fn() } } }),
      signInWithOtp: vi.fn().mockResolvedValue({ error: null }),
      exchangeCodeForSession: vi.fn().mockResolvedValue({ data: { session }, error: null }),
      signOut: vi.fn().mockResolvedValue({ error: null }),
    } };
    TestBed.configureTestingModule({ providers: [
      { provide: AUTH_CLIENT, useValue: Promise.resolve(client) },
      { provide: AUTH_CALLBACK, useValue: { code: 'one-time-code', failed: false } },
    ] });
  });

  it('sends a magic link to a fixed same-origin callback and permits first-time registration', async () => {
    const auth = TestBed.inject(Auth);
    await auth.ready;
    await auth.sendLink(' owner@example.com ');
    expect(client.auth.signInWithOtp).toHaveBeenCalledWith({ email: 'owner@example.com',
      options: { shouldCreateUser: true, emailRedirectTo: `${location.origin}/auth/callback` } });
  });

  it('consumes the code before exchanging it and rejects a replay', async () => {
    const auth = TestBed.inject(Auth);
    await auth.ready;
    await auth.completeCallback();
    expect(TestBed.inject(AUTH_CALLBACK).code).toBe('');
    expect(auth.signedIn()).toBe(true);
    await expect(auth.completeCallback()).rejects.toThrow('Invalid callback');
    expect(client.auth.exchangeCodeForSession).toHaveBeenCalledTimes(1);
  });

  it('uses the latest refreshed session for each API request and reacts to cross-tab logout', async () => {
    const auth = TestBed.inject(Auth);
    await auth.ready;
    client.auth.getSession.mockResolvedValue({ data: { session }, error: null });
    expect(await auth.accessToken()).toBe('test-access-token');
    client.auth.getSession.mockResolvedValue({ data: { session: { access_token: 'refreshed-token' } }, error: null });
    expect(await auth.accessToken()).toBe('refreshed-token');
    client.auth.onAuthStateChange.mock.calls[0][0]('SIGNED_OUT', null);
    expect(auth.signedIn()).toBe(false);
  });

  it('ends the local session on logout and surfaces failed logout without pretending success', async () => {
    client.auth.getSession.mockResolvedValue({ data: { session }, error: null });
    const auth = TestBed.inject(Auth);
    await auth.ready;
    client.auth.signOut.mockResolvedValueOnce({ error: new Error('private upstream detail') });
    await expect(auth.signOut()).rejects.toThrow('Logout failed');
    expect(auth.signedIn()).toBe(true);
    await auth.signOut();
    expect(auth.signedIn()).toBe(false);
    expect(client.auth.signOut).toHaveBeenCalledWith({ scope: 'local' });
  });

  it('does not expose provider error details or establish a session after a failed exchange', async () => {
    client.auth.exchangeCodeForSession.mockResolvedValue({ data: { session: null }, error: new Error('private-code') });
    const auth = TestBed.inject(Auth);
    await auth.ready;
    await expect(auth.completeCallback()).rejects.toThrow('Invalid callback');
    expect(auth.signedIn()).toBe(false);
  });
});

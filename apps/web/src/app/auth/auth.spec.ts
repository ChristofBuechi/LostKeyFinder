import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AUTH_CALLBACK, AUTH_CLIENT, Auth } from './auth';

describe('Firebase email-link session lifecycle', () => {
  const user = { getIdToken: vi.fn().mockResolvedValue('token') };
  let client: { auth: { currentUser: typeof user | null; authStateReady: ReturnType<typeof vi.fn> };
    sdk: Record<string, ReturnType<typeof vi.fn>> };
  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
    user.getIdToken.mockResolvedValue('token');
    client = {
      auth: { currentUser: null, authStateReady: vi.fn().mockResolvedValue(undefined) },
      sdk: { onIdTokenChanged: vi.fn().mockReturnValue(vi.fn()),
        sendSignInLinkToEmail: vi.fn().mockResolvedValue(undefined),
        isSignInWithEmailLink: vi.fn().mockReturnValue(true),
        signInWithEmailLink: vi.fn().mockResolvedValue({ user }), signOut: vi.fn().mockResolvedValue(undefined) },
    };
    TestBed.configureTestingModule({ providers: [
      { provide: AUTH_CLIENT, useValue: Promise.resolve(client) },
      { provide: AUTH_CALLBACK, useValue: { link: 'firebase-link', failed: false } },
    ] });
  });

  it('sends a link to the fixed callback and stores the email outside the URL', async () => {
    const auth = TestBed.inject(Auth);
    await auth.sendLink(' owner@example.com ');
    expect(client.sdk['sendSignInLinkToEmail']).toHaveBeenCalledWith(client.auth, 'owner@example.com', {
      url: `${location.origin}/auth/callback`, handleCodeInApp: true,
    });
    expect(localStorage.getItem('firebaseEmailForSignIn')).toBe('owner@example.com');
  });

  it('completes once using the locally stored email and rejects replay', async () => {
    localStorage.setItem('firebaseEmailForSignIn', 'owner@example.com');
    const auth = TestBed.inject(Auth);
    await auth.completeCallback();
    expect(auth.signedIn()).toBe(true);
    expect(TestBed.inject(AUTH_CALLBACK).link).toBe('');
    expect(localStorage.getItem('firebaseEmailForSignIn')).toBeNull();
    await expect(auth.completeCallback()).rejects.toThrow('Invalid callback');
    expect(client.sdk['signInWithEmailLink']).toHaveBeenCalledTimes(1);
  });

  it('asks for email on another device without consuming the link', async () => {
    const auth = TestBed.inject(Auth);
    await auth.completeCallback();
    expect(auth.needsEmail()).toBe(true);
    expect(client.sdk['signInWithEmailLink']).not.toHaveBeenCalled();
    await auth.completeCallback('owner@example.com');
    expect(auth.signedIn()).toBe(true);
  });

  it('uses fresh Firebase ID tokens and reacts to logout in another tab', async () => {
    client.auth.currentUser = user;
    const auth = TestBed.inject(Auth);
    expect(await auth.accessToken()).toBe('token');
    user.getIdToken.mockResolvedValue('refreshed');
    expect(await auth.accessToken()).toBe('refreshed');
    client.sdk['onIdTokenChanged'].mock.calls[0][1](null);
    expect(auth.signedIn()).toBe(false);
  });

  it('preserves state after failed logout and hides provider errors', async () => {
    client.auth.currentUser = user;
    const auth = TestBed.inject(Auth);
    await auth.ready;
    client.sdk['signOut'].mockRejectedValueOnce(new Error('private detail'));
    await expect(auth.signOut()).rejects.toThrow('Logout failed');
    expect(auth.signedIn()).toBe(true);
    await auth.signOut();
    expect(auth.signedIn()).toBe(false);
  });

  it('does not establish a session after a failed exchange', async () => {
    client.sdk['signInWithEmailLink'].mockRejectedValue(new Error('private detail'));
    const auth = TestBed.inject(Auth);
    await expect(auth.completeCallback('owner@example.com')).rejects.toThrow('Invalid callback');
    expect(auth.signedIn()).toBe(false);
  });
});

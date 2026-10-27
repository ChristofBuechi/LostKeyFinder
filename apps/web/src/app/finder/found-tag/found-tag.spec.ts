import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Auth } from '../../auth/auth';
import { FoundTag } from './found-tag';

const id = '550e8400-e29b-41d4-a716-446655440000';
describe('One scan entry for registration and existing tags', () => {
  const signedIn = signal(false);
  const sendLink = vi.fn();
  beforeEach(() => {
    localStorage.clear(); signedIn.set(false); sendLink.mockReset().mockResolvedValue(undefined);
    const paramMap = convertToParamMap({ publicId: id });
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: Auth, useValue: { signedIn, configured: true, sendLink } },
      { provide: ActivatedRoute, useValue: { paramMap: of(paramMap), snapshot: { paramMap } } }] });
  });
  async function respond(url: string, body: object) {
    const request = await vi.waitFor(() => TestBed.inject(HttpTestingController).expectOne(url));
    request.flush(body); return request;
  }

  it('sends the flow continuation then leaves the original browser on the success page', async () => {
    const fixture = TestBed.createComponent(FoundTag);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await respond(`/api/v1/public/tags/${id}`, { action: 'REGISTER' });
    await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.value = 'owner@example.com'; input.dispatchEvent(new Event('input')); await fixture.whenStable();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const request = await respond(`/api/v1/public/tags/${id}/registration-drafts`, { flowId: id, expiresAt: new Date(Date.now() + 3600000).toISOString() });
    await fixture.whenStable();
    expect(request.request.body).toEqual({ email: 'owner@example.com' });
    expect(sendLink).toHaveBeenCalledWith('owner@example.com', id);
    expect(navigate).toHaveBeenCalledWith('/login/link-sent', { replaceUrl: true });
    TestBed.inject(HttpTestingController).verify();
  });

  it('offers a private found report for an existing tag', async () => {
    const fixture = TestBed.createComponent(FoundTag);
    await respond(`/api/v1/public/tags/${id}`, { action: 'REPORT_FOUND' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Fund melden');
    expect(fixture.nativeElement.querySelector('textarea')).not.toBeNull();
    expect(sendLink).not.toHaveBeenCalled(); TestBed.inject(HttpTestingController).verify();
  });

  it('allows an authenticated user to confirm direct registration without another email', async () => {
    signedIn.set(true);
    const fixture = TestBed.createComponent(FoundTag);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await respond(`/api/v1/public/tags/${id}`, { action: 'REGISTER' });
    await respond('/api/v1/me', { id, email: 'owner@example.com', isAdmin: false, status: 'ACTIVE' });
    await fixture.whenStable(); fixture.nativeElement.querySelector('button').click();
    await respond(`/api/v1/tags/${id}/register`, { id });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(`/tags/${id}`, { replaceUrl: true });
    expect(sendLink).not.toHaveBeenCalled(); TestBed.inject(HttpTestingController).verify();
  });
});

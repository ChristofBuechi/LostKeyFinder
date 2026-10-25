import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { Auth } from '../../auth/auth';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TagRegistration } from './tag-registration';

const id = '550e8400-e29b-41d4-a716-446655440000';
const view = { flowId: id, publicId: id, completed: false, expiresAt: new Date(Date.now() + 3600000).toISOString() };
const user = { id, email: 'owner@example.com', isAdmin: false, status: 'ACTIVE', createdAt: '', updatedAt: '' };

describe('Registration in the email browser', () => {
  beforeEach(() => {
    localStorage.clear();
    const paramMap = convertToParamMap({ flowId: id });
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: Auth, useValue: { signedIn: signal(true) } },
      { provide: ActivatedRoute, useValue: { paramMap: of(paramMap), snapshot: { paramMap } } }] });
  });
  async function respond(url: string, body: object, status = 200) {
    const request = await vi.waitFor(() => TestBed.inject(HttpTestingController).expectOne(url));
    request.flush(body, { status, statusText: 'Response' }); return request;
  }

  it('loads with no original-browser storage and claims only after explicit confirmation', async () => {
    const fixture = TestBed.createComponent(TagRegistration);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await respond(`/api/v1/tag-registrations/${id}`, view);
    await respond('/api/v1/me', user);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain(user.email);
    TestBed.inject(HttpTestingController).expectNone(request => request.method === 'POST');
    fixture.nativeElement.querySelector('button').click();
    await respond(`/api/v1/tag-registrations/${id}/complete`, { id });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(`/tags/${id}`, { replaceUrl: true });
    TestBed.inject(HttpTestingController).verify();
  });

  it('hides confirmation when an expired or foreign flow is unavailable', async () => {
    const fixture = TestBed.createComponent(TagRegistration);
    await respond(`/api/v1/tag-registrations/${id}`, {}, 404);
    await respond('/api/v1/me', user);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('abgelaufen');
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
    TestBed.inject(HttpTestingController).verify();
  });

  it('does not navigate after a late completion when the page has been closed', async () => {
    const fixture = TestBed.createComponent(TagRegistration);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await respond(`/api/v1/tag-registrations/${id}`, view); await respond('/api/v1/me', user);
    await fixture.whenStable(); fixture.nativeElement.querySelector('button').click();
    const request = await vi.waitFor(() => TestBed.inject(HttpTestingController).expectOne(`/api/v1/tag-registrations/${id}/complete`));
    fixture.destroy(); request.flush({ id }); await Promise.resolve();
    expect(navigate).not.toHaveBeenCalled();
    TestBed.inject(HttpTestingController).verify();
  });
});

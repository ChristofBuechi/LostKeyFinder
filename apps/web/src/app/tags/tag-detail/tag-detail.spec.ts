import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Auth } from '../../auth/auth';
import { TagDetail } from './tag-detail';

describe('Private tag detail', () => {
  it('loads the authorized detail and clears it after logout in another tab', async () => {
    const id = '550e8400-e29b-41d4-a716-446655440000';
    const signedIn = signal(true);
    const paramMap = convertToParamMap({ tagId: id });
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: Auth, useValue: { signedIn } },
      { provide: ActivatedRoute, useValue: { paramMap: of(paramMap), snapshot: { paramMap, data: {} } } }] });
    const fixture = TestBed.createComponent(TagDetail);
    const http = TestBed.inject(HttpTestingController);
    const request = await vi.waitFor(() => http.expectOne(`/api/v1/tags/${id}`));
    request.flush({ id, publicId: id, label: 'Private keys', origin: 'SELF_CREATED', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z', registeredAt: null });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Private keys');
    signedIn.set(false); await fixture.whenStable();
    expect(fixture.nativeElement.textContent).not.toContain('Private keys');
    http.verify();
  });
});

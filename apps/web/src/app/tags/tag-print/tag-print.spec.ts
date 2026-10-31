import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { Auth } from '../../auth/auth';
import { TagPrint } from './tag-print';
const id = '550e8400-e29b-41d4-a716-446655440000';
const tag = { id, publicId: id, label: 'Keys', origin: 'SELF_CREATED', status: 'ACTIVE' };
describe('Tag print selection', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: Auth, useValue: { signedIn: signal(true), firebaseUserId: signal('user') } },
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } }] });
  });
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  it('only offers own printable origins and preserves selection across pages', async () => {
    const fixture = TestBed.createComponent(TagPrint);
    const http = TestBed.inject(HttpTestingController);
    (await vi.waitFor(() => http.expectOne('/api/v1/tags?page=1'))).flush({ items: [tag, { ...tag, id: 'hardware', origin: 'PREPRODUCED' }, { ...tag, id: 'deleted', deletedAt: 'today' }], page: 1, totalPages: 2 });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('.tag-choice')).toHaveLength(1);
    (fixture.nativeElement.querySelector('input[type=checkbox]') as HTMLInputElement).click();
    await fixture.whenStable();
    const next = fixture.nativeElement.querySelector('nav button:last-child') as HTMLButtonElement;
    next.click();
    (await vi.waitFor(() => http.expectOne('/api/v1/tags?page=2'))).flush({ items: [{ ...tag, id: 'next', label: 'Wallet' }], page: 2, totalPages: 2 });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('1 / 25');
    (fixture.nativeElement.querySelector('input[type=checkbox]') as HTMLInputElement).click();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('2 / 25');
    fixture.destroy();
  });
  it('uses the protected owner endpoint for a detail link and shows access errors', async () => {
    TestBed.overrideProvider(ActivatedRoute, { useValue: { snapshot: { queryParamMap: convertToParamMap({ tagId: id }) } } });
    const fixture = TestBed.createComponent(TagPrint);
    (await vi.waitFor(() => TestBed.inject(HttpTestingController).expectOne(`/api/v1/tags/${id}`))).flush({}, { status: 404, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.tag-choice')).toHaveLength(0);
    fixture.destroy();
  });
});

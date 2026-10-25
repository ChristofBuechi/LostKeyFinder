import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { Auth } from '../../auth/auth';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TagBatches } from './tag-batches';

const id = '550e8400-e29b-41d4-a716-446655440000';
const batch = { id, count: 100, label: 'Batch', createdAt: '2026-01-01T00:00:00Z', releasedAt: null };
describe('Production batches', () => {
  beforeEach(() => {
    localStorage.clear(); TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: Auth, useValue: { signedIn: signal(true) } }] });
  });
  async function respond(url: string, body: object | string, status = 200) {
    const request = await vi.waitFor(() => TestBed.inject(HttpTestingController).expectOne(url));
    request.flush(body, { status, statusText: 'Response' }); return request;
  }
  it('retries uncertain batch creation with a stable request', async () => {
    const fixture = TestBed.createComponent(TagBatches);
    await respond('/api/v1/admin/tag-batches', { items: [], nextCursor: null }); await fixture.whenStable();
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const first = await respond('/api/v1/admin/tag-batches', {}, 503); await fixture.whenStable();
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const retry = await respond('/api/v1/admin/tag-batches', batch);
    expect(retry.request.headers.get('Idempotency-Key')).toBe(first.request.headers.get('Idempotency-Key'));
    expect(retry.request.body).toEqual({ count: 100, label: '' });
    await respond('/api/v1/admin/tag-batches', { items: [batch], nextCursor: null }); await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Batch'); TestBed.inject(HttpTestingController).verify();
  });
  it('requires a deliberate quality confirmation before release', async () => {
    const fixture = TestBed.createComponent(TagBatches);
    await respond('/api/v1/admin/tag-batches', { items: [batch], nextCursor: null }); await fixture.whenStable();
    const review = [...fixture.nativeElement.querySelectorAll('button')].find((b: unknown) => (b as HTMLButtonElement).textContent.includes('vorbereiten')) as HTMLButtonElement;
    review.click(); await fixture.whenStable();
    const panel = fixture.nativeElement.querySelector('.release-panel') as HTMLElement;
    expect(panel.querySelector('button')!.disabled).toBe(true);
    panel.querySelector('input')!.click(); await fixture.whenStable();
    expect(panel.querySelector('button')!.disabled).toBe(false);
    panel.querySelector('button')!.click();
    await respond(`/api/v1/admin/tag-batches/${id}/release`, { ...batch, releasedAt: '2026-01-02T00:00:00Z' });
    await respond('/api/v1/admin/tag-batches', { items: [{ ...batch, releasedAt: '2026-01-02T00:00:00Z' }], nextCursor: null }); await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.release-panel')).toBeNull(); TestBed.inject(HttpTestingController).verify();
  });
});

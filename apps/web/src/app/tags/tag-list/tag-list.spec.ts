import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Auth } from '../../auth/auth';
import { LanguageService } from '../../core/language.service';
import { QR_ENCODER, TagList } from './tag-list';

const id = '550e8400-e29b-41d4-a716-446655440000';
const tag = { id, publicId: id, origin: 'SELF_CREATED', status: 'ACTIVE', label: 'Keys',
  primaryOwnerUserId: 'owner', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' };

describe('Tag management', () => {
  const signedIn = signal(true);
  const encode = vi.fn(async () => 'data:image/png;base64,AAAA');
  beforeEach(() => {
    localStorage.clear();
    signedIn.set(true);
    encode.mockClear();
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(),
      provideRouter([]), { provide: ActivatedRoute, useValue: { snapshot: { data: {} } } },
      { provide: QR_ENCODER, useValue: async () => ({ toDataURL: encode }) },
      { provide: Auth, useValue: { signedIn } }] });
  });

  async function response(url: string, body: object, status = 200) {
    const request = await vi.waitFor(() => TestBed.inject(HttpTestingController).expectOne(url));
    request.flush(body, { status, statusText: status === 200 ? 'OK' : 'Failed' });
    return request;
  }

  it('creates a tag once and retries uncertain delivery with the same key and label', async () => {
    const fixture = TestBed.createComponent(TagList);
    await response('/api/v1/tags', { items: [], nextCursor: null });
    await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('#tag-label') as HTMLInputElement;
    input.value = 'Keys';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const first = await response('/api/v1/tags', {}, 503);
    await fixture.whenStable();
    expect(first.request.method).toBe('POST');
    expect(input.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('denselben Auftrag');
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    const retry = await response('/api/v1/tags', tag);
    expect(retry.request.headers.get('Idempotency-Key')).toBe(first.request.headers.get('Idempotency-Key'));
    expect(retry.request.body).toEqual(first.request.body);
    await response('/api/v1/tags', { items: [tag], nextCursor: null });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('tbody').textContent).toContain('Keys');
    expect(input.disabled).toBe(false);
    TestBed.inject(HttpTestingController).verify();
  });

  it('deactivates an owned tag and downloads a QR for the canonical public link', async () => {
    const fixture = TestBed.createComponent(TagList);
    await response('/api/v1/tags', { items: [tag], nextCursor: null });
    await fixture.whenStable();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const buttons = fixture.nativeElement.querySelectorAll('td.actions button') as NodeListOf<HTMLButtonElement>;
    buttons[0].click();
    await fixture.whenStable();
    expect(encode).toHaveBeenCalledWith(`${location.origin}/f/${id}`, expect.objectContaining({ margin: 4 }));
    expect(click).toHaveBeenCalledOnce();
    click.mockRestore();
    buttons[1].click();
    const mutation = await response(`/api/v1/tags/${id}/status`, { ...tag, status: 'INACTIVE' });
    expect(mutation.request.body).toEqual({ status: 'INACTIVE' });
    await response('/api/v1/tags', { items: [{ ...tag, status: 'INACTIVE' }], nextCursor: null });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Aktivieren');
    TestBed.inject(HttpTestingController).verify();
  });

  it('admin filters use server queries and page cursors without exposing owner-only actions', async () => {
    TestBed.overrideProvider(ActivatedRoute, { useValue: { snapshot: { data: { admin: true } } } });
    const fixture = TestBed.createComponent(TagList);
    await response('/api/v1/admin/tags', { items: [tag], nextCursor: `1_${id}` });
    await response('/api/v1/admin/tags/summary', { total: 1, assigned: 1, unassigned: 0, blocked: 0, retired: 0, prepared: 0, released: 0 });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#tag-label')).toBeNull();
    expect(fixture.nativeElement.querySelector('.actions')).toBeNull();
    const next = [...fixture.nativeElement.querySelectorAll('button')].find((b: unknown) => (b as HTMLButtonElement).textContent.includes('Nächste')) as HTMLButtonElement;
    next.click();
    await response(`/api/v1/admin/tags?cursor=1_${id}`, { items: [], nextCursor: null });
    await fixture.whenStable();
    const select = fixture.nativeElement.querySelector('#origin') as HTMLSelectElement;
    select.value = 'PREPRODUCED';
    select.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await response('/api/v1/admin/tags?origin=PREPRODUCED', { items: [], nextCursor: null });
    await response('/api/v1/admin/tags/summary?origin=PREPRODUCED', { total: 0, assigned: 0, unassigned: 0, blocked: 0, retired: 0, prepared: 0, released: 0 });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Keine Tags gefunden');
    TestBed.inject(HttpTestingController).verify();
  });

  it('rejects invalid search input and renders backend access denial without data', async () => {
    TestBed.overrideProvider(ActivatedRoute, { useValue: { snapshot: { data: { admin: true } } } });
    const fixture = TestBed.createComponent(TagList);
    await response('/api/v1/admin/tags', {}, 403);
    await response('/api/v1/admin/tags/summary', {}, 403);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Berechtigung');
    expect(fixture.nativeElement.querySelector('tbody')).toBeNull();
    const input = fixture.nativeElement.querySelector('#public-id') as HTMLInputElement;
    input.value = 'invalid';
    input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await fixture.whenStable();
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('gültige UUID');
    TestBed.inject(HttpTestingController).verify();
  });

  it('does not render a cached owner list after logout and supports both languages', async () => {
    const fixture = TestBed.createComponent(TagList);
    await response('/api/v1/tags', { items: [tag], nextCursor: null });
    await fixture.whenStable();
    TestBed.inject(LanguageService).toggle();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('My tags');
    signedIn.set(false);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('tbody')).toBeNull();
    TestBed.inject(HttpTestingController).verify();
  });
});

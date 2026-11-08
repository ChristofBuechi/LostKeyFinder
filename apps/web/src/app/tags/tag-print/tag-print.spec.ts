import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { Auth } from '../../auth/auth';
import { TAG_PDF, TagPrint } from './tag-print';
const id = '550e8400-e29b-41d4-a716-446655440000';
const tag = { id, publicId: id, label: 'Keys', origin: 'SELF_CREATED', status: 'ACTIVE' };
describe('Tag print selection', () => {
  const tagPdf = vi.fn<typeof import('./tag-pdf').tagPdf>();
  beforeEach(() => {
    tagPdf.mockReset();
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: TAG_PDF, useValue: tagPdf },
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
    expect(fixture.nativeElement.textContent).toContain('0 / 25');
    expect((fixture.nativeElement.querySelector('input[type=checkbox]') as HTMLInputElement).checked).toBe(false);
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
  it('limits selection to 25 tags across pages and allows replacing a selected tag', async () => {
    const fixture = TestBed.createComponent(TagPrint);
    const http = TestBed.inject(HttpTestingController);
    const tags = Array.from({ length: 25 }, (_, index) => ({ ...tag, id: String(index) }));
    (await vi.waitFor(() => http.expectOne('/api/v1/tags?page=1'))).flush({ items: tags, page: 1, totalPages: 2 });
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;
    for (const checkbox of element.querySelectorAll<HTMLInputElement>('input[type=checkbox]')) {
      checkbox.click();
      await fixture.whenStable();
    }
    element.querySelector<HTMLButtonElement>('nav button:last-child')!.click();
    (await vi.waitFor(() => http.expectOne('/api/v1/tags?page=2'))).flush({ items: [tags[0], { ...tag, id: 'extra' }], page: 2, totalPages: 2 });
    await fixture.whenStable();
    const [selected, extra] = element.querySelectorAll<HTMLInputElement>('input[type=checkbox]');
    expect(element.textContent).toContain('25 / 25');
    expect(selected.checked).toBe(true);
    expect(selected.disabled).toBe(false);
    expect(extra.disabled).toBe(true);
    selected.click();
    await fixture.whenStable();
    expect(extra.disabled).toBe(false);
    extra.click();
    await fixture.whenStable();
    expect(element.textContent).toContain('25 / 25');
    expect(extra.checked).toBe(true);
    expect(selected.disabled).toBe(true);
    fixture.destroy();
  });
  it.each([true, false])('fixes the only printable tag without a checkbox (direct link: %s)', async direct => {
    if (direct) {
      TestBed.overrideProvider(ActivatedRoute, { useValue: { snapshot: { queryParamMap: convertToParamMap({ tagId: id }) } } });
    }
    const fixture = TestBed.createComponent(TagPrint);
    const http = TestBed.inject(HttpTestingController);
    const request = await vi.waitFor(() => http.expectOne(direct ? `/api/v1/tags/${id}` : '/api/v1/tags?page=1'));
    request.flush(direct ? tag : { items: [tag], page: 1, totalPages: 1 });
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).toContain('Keys');
    expect(element.querySelector('input[type=checkbox]')).toBeNull();
    expect(element.querySelectorAll('input[type=radio]')).toHaveLength(2);
    expect(element.querySelector('button')?.disabled).toBe(false);
    fixture.destroy();
  });
  it.each([
    { origin: 'SELF_CREATED', status: 'ACTIVE', deletedAt: null, printable: true },
    { origin: 'SELF_CREATED', status: 'INACTIVE', deletedAt: null, printable: true },
    { origin: 'SELF_CREATED', status: 'BLOCKED', deletedAt: null, printable: false },
    { origin: 'SELF_CREATED', status: 'RETIRED', deletedAt: null, printable: false },
    { origin: 'SELF_CREATED', status: 'ACTIVE', deletedAt: '2026-01-01T00:00:00Z', printable: false },
    { origin: 'PREPRODUCED', status: 'ACTIVE', deletedAt: null, printable: false },
  ])('applies print eligibility to a direct link: %j', async ({ printable, ...fields }) => {
    TestBed.overrideProvider(ActivatedRoute, { useValue: { snapshot: { queryParamMap: convertToParamMap({ tagId: id }) } } });
    const fixture = TestBed.createComponent(TagPrint);
    (await vi.waitFor(() => TestBed.inject(HttpTestingController).expectOne(`/api/v1/tags/${id}`))).flush({ ...tag, ...fields });
    await fixture.whenStable();
    expect((fixture.nativeElement.querySelector('button') as HTMLButtonElement).disabled).toBe(!printable);
    fixture.destroy();
  });
  it('exports the automatically selected public identity and reports PDF failures', async () => {
    TestBed.overrideProvider(ActivatedRoute, { useValue: { snapshot: { queryParamMap: convertToParamMap({ tagId: id }) } } });
    tagPdf.mockRejectedValueOnce(new Error('PDF generation failed'));
    const fixture = TestBed.createComponent(TagPrint);
    const publicId = '650e8400-e29b-41d4-a716-446655440000';
    (await vi.waitFor(() => TestBed.inject(HttpTestingController).expectOne(`/api/v1/tags/${id}`))).flush({ ...tag, publicId });
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    await vi.waitFor(() => expect(tagPdf).toHaveBeenCalledExactlyOnceWith(
      [publicId], location.origin, true, 'Gefunden?\nBitte QR-Code scannen.'
    ));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
    expect((fixture.nativeElement.querySelector('button') as HTMLButtonElement).disabled).toBe(false);
    fixture.destroy();
  });
  it('rejects an invalid direct link without making a request', async () => {
    TestBed.overrideProvider(ActivatedRoute, { useValue: { snapshot: { queryParamMap: convertToParamMap({ tagId: 'invalid' }) } } });
    const fixture = TestBed.createComponent(TagPrint);
    await fixture.whenStable();
    TestBed.inject(HttpTestingController).expectNone(request => request.url.startsWith('/api/v1/tags'));
    expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
    expect((fixture.nativeElement.querySelector('button:last-of-type') as HTMLButtonElement).disabled).toBe(true);
    fixture.destroy();
  });
  it('uses the protected owner endpoint for a detail link and shows access errors', async () => {
    TestBed.overrideProvider(ActivatedRoute, { useValue: { snapshot: { queryParamMap: convertToParamMap({ tagId: id }) } } });
    const fixture = TestBed.createComponent(TagPrint);
    (await vi.waitFor(() => TestBed.inject(HttpTestingController).expectOne(`/api/v1/tags/${id}`))).flush({}, { status: 404, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.tag-choice')).toHaveLength(0);
    expect((fixture.nativeElement.querySelector('button:last-of-type') as HTMLButtonElement).disabled).toBe(true);
    fixture.destroy();
  });
});

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
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
    const history = await vi.waitFor(() => http.expectOne(`/api/v1/tags/${id}/history?page=1`));
    history.flush({ items: [{ id: 'event', action: 'TAG_DEACTIVATED', createdAt: '2026-01-02T00:00:00Z', actorUserId: null, changes: [{ field: 'status', before: 'ACTIVE', after: 'INACTIVE' }] }], page: 1, totalPages: 2 });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Tag deaktiviert');
    expect(fixture.nativeElement.querySelector('details').textContent).toContain('Vorher: Aktiv');
    expect(fixture.nativeElement.querySelector('details').textContent).toContain('Nachher: Inaktiv');
    fixture.nativeElement.querySelector('.history-pages button:last-child').click();
    const nextHistory = await vi.waitFor(() => http.expectOne(`/api/v1/tags/${id}/history?page=2`));
    nextHistory.flush({ items: [{ id, action: 'TAG_CREATED', createdAt: '2026-01-01T00:00:00Z', actorUserId: null, changes: [] }], page: 2, totalPages: 2 });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Tag erstellt');
    expect(fixture.nativeElement.textContent).toContain('Seite 2 von 2');
    expect(fixture.nativeElement.textContent).toContain('Private keys');
    const actionBar = fixture.nativeElement.querySelector('.detail-actions') as HTMLElement;
    expect(actionBar.querySelector('a.tag-action')?.getAttribute('href')).toBe('/tags');
    expect(actionBar.querySelector('app-tag-removal button.tag-action--danger')).not.toBeNull();
    expect(actionBar.compareDocumentPosition(fixture.nativeElement.querySelector('#tag-history-title')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.nativeElement.querySelector('app-tag-removal button').click();
    const deletion = await vi.waitFor(() => http.expectOne(`/api/v1/tags/${id}`));
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith('/tags', { replaceUrl: true });
    confirm.mockRestore(); navigate.mockRestore();
    signedIn.set(false); await fixture.whenStable();
    expect(fixture.nativeElement.textContent).not.toContain('Private keys');
    http.verify();
  });  it('keeps admin details open after deletion and reloads data and history', async () => {
    const id = '550e8400-e29b-41d4-a716-446655440000';
    const paramMap = convertToParamMap({ tagId: id });
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: Auth, useValue: { signedIn: signal(true) } },
      { provide: ActivatedRoute, useValue: { paramMap: of(paramMap), snapshot: { paramMap, data: { admin: true } } } }] });
    const fixture = TestBed.createComponent(TagDetail);
    const http = TestBed.inject(HttpTestingController);
    const tag = { id, publicId: id, label: 'Hardware', origin: 'PREPRODUCED', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z', registeredAt: null };
    (await vi.waitFor(() => http.expectOne(`/api/v1/admin/tags/${id}`))).flush(tag);
    (await vi.waitFor(() => http.expectOne(`/api/v1/admin/tags/${id}/history?page=1`))).flush({ items: [], page: 1, totalPages: 1 });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.detail-actions a').getAttribute('href')).toBe('/admin/tags');
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.nativeElement.querySelector('app-tag-removal button').click();
    const deletion = await vi.waitFor(() => http.expectOne(`/api/v1/admin/tags/${id}`));
    expect(deletion.request.method).toBe('DELETE'); deletion.flush(null, { status: 204, statusText: 'No Content' });
    (await vi.waitFor(() => http.expectOne(`/api/v1/admin/tags/${id}`))).flush({ ...tag, status: 'RETIRED', deletedAt: '2026-10-25T00:00:00Z' });
    (await vi.waitFor(() => http.expectOne(`/api/v1/admin/tags/${id}/history?page=1`))).flush({ items: [], page: 1, totalPages: 1 });
    await fixture.whenStable();
    expect(navigate).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Gelöscht');
    expect(fixture.nativeElement.querySelector('app-tag-removal button')).toBeNull();
    confirm.mockRestore(); navigate.mockRestore(); http.verify();
  });

});

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TagResponse } from '@lost-key-finder/api-client';
import { TagRemoval } from './tag-removal';

const id = '550e8400-e29b-41d4-a716-446655440000';

describe('Owner tag removal', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()] });
  });
  afterEach(() => vi.restoreAllMocks());

  it.each(['SELF_CREATED', 'PREPRODUCED'] as const)('confirms %s removal and emits only after successful completion', async origin => {
    const fixture = TestBed.createComponent(TagRemoval);
    fixture.componentRef.setInput('tag', { id, publicId: id, origin, status: 'ACTIVE' } as TagResponse);
    const removed = vi.fn(); fixture.componentInstance.removed.subscribe(removed);
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    await fixture.whenStable();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    button.click(); await fixture.whenStable();
    const http = TestBed.inject(HttpTestingController);
    http.expectNone(request => request.method !== 'GET');
    expect(removed).not.toHaveBeenCalled();
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining(id));
    confirm.mockReturnValue(true); button.click();
    const url = `/api/v1/tags/${id}${origin === 'SELF_CREATED' ? '' : '/release'}`;
    const request = await vi.waitFor(() => http.expectOne(url));
    expect(request.request.method).toBe(origin === 'SELF_CREATED' ? 'DELETE' : 'POST');
    await fixture.whenStable(); expect(button.disabled).toBe(true);
    request.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable(); expect(removed).toHaveBeenCalledOnce();
    http.verify();
  });

  it.each(['SELF_CREATED', 'PREPRODUCED'] as const)('uses admin deletion for %s including blocked tags', async origin => {
    const fixture = TestBed.createComponent(TagRemoval);
    fixture.componentRef.setInput('admin', true);
    fixture.componentRef.setInput('tag', { id, publicId: id, origin, status: 'BLOCKED' } as TagResponse);
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    await fixture.whenStable(); fixture.nativeElement.querySelector('button').click();
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('Administrator'));
    const http = TestBed.inject(HttpTestingController);
    const request = await vi.waitFor(() => http.expectOne(`/api/v1/admin/tags/${id}`));
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    fixture.componentRef.setInput('tag', { id, publicId: id, origin, status: 'RETIRED', deletedAt: '2026-10-25T00:00:00Z' } as TagResponse);
    await fixture.whenStable(); expect(fixture.nativeElement.querySelector('button')).toBeNull();
    http.verify();
  });

  it('retains the view after a failed request and hides actions for blocked or retired tags', async () => {
    const fixture = TestBed.createComponent(TagRemoval);
    fixture.componentRef.setInput('tag', { id, publicId: id, origin: 'SELF_CREATED', status: 'ACTIVE' } as TagResponse);
    const removed = vi.fn(); fixture.componentInstance.removed.subscribe(removed);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    await fixture.whenStable(); fixture.nativeElement.querySelector('button').click();
    const http = TestBed.inject(HttpTestingController);
    const request = await vi.waitFor(() => http.expectOne(`/api/v1/tags/${id}`));
    request.flush({}, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(removed).not.toHaveBeenCalled();
    for (const status of ['BLOCKED', 'RETIRED']) {
      fixture.componentRef.setInput('tag', { id, publicId: id, origin: 'SELF_CREATED', status } as TagResponse);
      await fixture.whenStable(); expect(fixture.nativeElement.querySelector('button')).toBeNull();
    }
    http.verify();
  });
});

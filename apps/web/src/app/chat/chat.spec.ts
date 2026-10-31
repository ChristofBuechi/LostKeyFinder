import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Auth } from '../auth/auth';
import { ReportFound } from './report-found';
import { ChatDetail } from './chat-detail';
const id = '550e8400-e29b-41d4-a716-446655440000';
const message = { id, chatId: id, text: 'Found your keys', senderUserId: 'finder', sequence: 1, createdAt: '2026-10-26T12:00:00Z', imageId: null };

describe('Finder reports and private chat', () => {
  const signedIn = signal(false);
  const sendLink = vi.fn();
  beforeEach(() => {
    localStorage.clear(); signedIn.set(false); sendLink.mockReset().mockResolvedValue(undefined);
    const paramMap = convertToParamMap({ chatId: id });
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: Auth, useValue: { signedIn, configured: true, sendLink } },
      { provide: ActivatedRoute, useValue: { paramMap: of(paramMap), queryParamMap: of(convertToParamMap({})), snapshot: { paramMap, queryParamMap: convertToParamMap({}) } } }] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); });
  async function respond(url: string, body: object) {
    const request = await vi.waitFor(() => TestBed.inject(HttpTestingController).expectOne(url));
    request.flush(body); return request;
  }

  it.each(['#finder-image', 'input[capture=environment]'])('uploads an image from %s and preserves a retry key when link delivery fails', async (selector) => {
    const fixture = TestBed.createComponent(ReportFound);
    fixture.componentRef.setInput('publicId', id);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await fixture.whenStable();
    const email = fixture.nativeElement.querySelector('input[type=email]') as HTMLInputElement;
    email.value = 'finder@example.com'; email.dispatchEvent(new Event('input'));
    const text = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    text.value = 'Found your keys'; text.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    const imageInput = fixture.nativeElement.querySelector(selector) as HTMLInputElement;
    const pick = vi.spyOn(imageInput, 'click');
    const buttons = fixture.nativeElement.querySelectorAll('.image-actions button');
    (buttons[selector === '#finder-image' ? 0 : 1] as HTMLButtonElement).click();
    expect(pick).toHaveBeenCalledOnce();
    Object.defineProperty(imageInput, 'files', { value: [new File(['image'], 'found.png', { type: 'image/png' })] });
    imageInput.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role=status]').textContent).toContain('found.png');
    const other = fixture.nativeElement.querySelector(selector === '#finder-image' ? 'input[capture]' : '#finder-image') as HTMLInputElement;
    other.dispatchEvent(new Event('change')); // Cancelling a picker keeps the selected attachment.
    sendLink.mockRejectedValueOnce(new Error('offline'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    const start = await respond(`/api/v1/public/tags/${id}/finder-drafts`, { flowId: id, expiresAt: new Date(Date.now() + 3600000).toISOString() });
    expect(start.request.params.has('email')).toBe(false);
    expect(start.request.params.has('text')).toBe(false);
    expect((start.request.body as FormData).get('image')).toBeInstanceOf(Blob);
    expect(((start.request.body as FormData).get('image') as File).name).toBe('found.png');
    expect((start.request.body as FormData).get('text')).toBe('Found your keys');
    expect(start.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    await vi.waitFor(async () => { await fixture.whenStable(); expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull(); });
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    await vi.waitFor(() => expect(sendLink).toHaveBeenCalledTimes(2));
    expect(sendLink).toHaveBeenLastCalledWith('finder@example.com', undefined, { finderReportId: id });
    expect(navigate).toHaveBeenCalledWith('/login/link-sent', { replaceUrl: true });
    fixture.destroy();
  });

  it('replaces or removes an attachment and rejects oversized camera photos', async () => {
    const fixture = TestBed.createComponent(ReportFound);
    fixture.componentRef.setInput('publicId', id);
    await fixture.whenStable();
    const gallery = fixture.nativeElement.querySelector('#finder-image') as HTMLInputElement;
    const camera = fixture.nativeElement.querySelector('input[capture]') as HTMLInputElement;
    const choose = async (input: HTMLInputElement, file: File) => {
      Object.defineProperty(input, 'files', { value: [file], configurable: true });
      input.dispatchEvent(new Event('change'));
      await fixture.whenStable();
    };
    await choose(gallery, new File(['image'], 'gallery.png', { type: 'image/png' }));
    await choose(camera, new File(['photo'], 'camera.jpg', { type: 'image/jpeg' }));
    expect(fixture.nativeElement.querySelector('[role=status]').textContent).toBe('camera.jpg');
    const remove = [...fixture.nativeElement.querySelectorAll('button')].find(button => (button as HTMLButtonElement).textContent?.includes('Bild entfernen')) as HTMLButtonElement;
    remove.click();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role=status]').textContent).toBe('');
    const oversized = new File(['photo'], 'large.jpg', { type: 'image/jpeg' });
    Object.defineProperty(oversized, 'size', { value: 5_000_001 });
    await choose(camera, oversized);
    expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[role=status]').textContent).toBe('');
    fixture.destroy();
  });

  it('opens the current conversation even when an old link contains a message reference', async () => {
    signedIn.set(true);
    Object.defineProperty(TestBed.inject(ActivatedRoute), 'queryParamMap', { value: of(convertToParamMap({ messageId: id })) });
    const fixture = TestBed.createComponent(ChatDetail);
    await respond(`/api/v1/chats/${id}`, { id, sequence: 2, participant: true, writable: true });
    await respond('/api/v1/me', { id: 'owner', isAdmin: false });
    await respond(`/api/v1/chats/${id}/messages`, { items: [message, { ...message, id: 'reply', sequence: 2, text: 'Latest reply' }], before: null });
    const read = await respond(`/api/v1/chats/${id}/read`, {});
    expect(read.request.body).toEqual({ through: 2 });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('.messages li')).toHaveLength(2);
    expect(fixture.nativeElement.textContent).toContain('Latest reply');
    fixture.destroy();
  });

  it('sends text with a stable idempotency key after failure and preserves the draft', async () => {
    signedIn.set(true);
    const fixture = TestBed.createComponent(ChatDetail);
    const view = { id, caseId: id, tagId: id, sequence: 1, unread: 1, writable: true, participant: true, canClose: true, closedAt: null };
    await respond(`/api/v1/chats/${id}`, view);
    await respond('/api/v1/me', { id: 'owner', isAdmin: false });
    await respond(`/api/v1/chats/${id}/messages`, { items: [message], before: null });
    await respond(`/api/v1/chats/${id}/read`, {});
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Found your keys');
    const input = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    input.value = 'Thanks!'; input.dispatchEvent(new Event('input')); await fixture.whenStable();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    const http = TestBed.inject(HttpTestingController);
    const first = await vi.waitFor(() => http.expectOne(`/api/v1/chats/${id}/messages`));
    const key = first.request.headers.get('Idempotency-Key');
    expect(first.request.params.has('text')).toBe(false);
    expect((first.request.body as FormData).get('text')).toBe('Thanks!');
    first.flush({}, { status: 503, statusText: 'offline' });
    await vi.waitFor(async () => { await fixture.whenStable(); expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull(); });
    expect(input.value).toBe('Thanks!');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    const retry = await vi.waitFor(() => http.expectOne(`/api/v1/chats/${id}/messages`));
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush({ ...message, id: key, sequence: 2, senderUserId: 'owner', text: 'Thanks!' });
    await respond(`/api/v1/chats/${id}`, { ...view, sequence: 2 });
    await respond(`/api/v1/chats/${id}/messages`, { items: [message, { ...message, id: key, sequence: 2, senderUserId: 'owner', text: 'Thanks!' }], before: null });
    await respond(`/api/v1/chats/${id}/read`, {});
    await fixture.whenStable();
    expect(input.value).toBe('');
    expect(fixture.nativeElement.querySelectorAll('.messages li')).toHaveLength(2);
    fixture.destroy();
  });


  it('starts a fresh report with a new key when a cached draft expires', async () => {
    const fixture = TestBed.createComponent(ReportFound);
    fixture.componentRef.setInput('publicId', id);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await fixture.whenStable();
    const email = fixture.nativeElement.querySelector('input[type=email]') as HTMLInputElement;
    email.value = 'finder@example.com'; email.dispatchEvent(new Event('input'));
    const text = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    text.value = 'Found your keys'; text.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    sendLink.mockRejectedValueOnce(new Error('offline'));
    const expiresAt = Date.now() + 3600000;
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    const first = await respond(`/api/v1/public/tags/${id}/finder-drafts`, { flowId: id, expiresAt: new Date(expiresAt).toISOString() });
    await vi.waitFor(async () => { await fixture.whenStable(); expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull(); });
    const clock = vi.spyOn(Date, 'now').mockReturnValue(expiresAt + 1);
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    clock.mockRestore();
    const nextId = '650e8400-e29b-41d4-a716-446655440001';
    const next = await respond(`/api/v1/public/tags/${id}/finder-drafts`, { flowId: nextId, expiresAt: new Date(Date.now() + 3600000).toISOString() });
    expect(next.request.headers.get('Idempotency-Key')).not.toBe(first.request.headers.get('Idempotency-Key'));
    await vi.waitFor(() => expect(sendLink).toHaveBeenLastCalledWith('finder@example.com', undefined, { finderReportId: nextId }));
    expect(text.value).toBe('Found your keys');
    fixture.destroy();
  });

  it('shows message loading failures and allows retry without throwing or hiding the error', async () => {
    signedIn.set(true);
    const fixture = TestBed.createComponent(ChatDetail);
    const view = { id, caseId: id, tagId: id, sequence: 1, unread: 1, writable: true, participant: true, canClose: true, closedAt: null };
    await respond(`/api/v1/chats/${id}`, view);
    await respond('/api/v1/me', { id: 'owner', isAdmin: false });
    const failed = await vi.waitFor(() => TestBed.inject(HttpTestingController).expectOne(`/api/v1/chats/${id}/messages`));
    failed.flush({}, { status: 503, statusText: 'offline' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
    const retry = [...fixture.nativeElement.querySelectorAll('button')].find(button => (button as HTMLButtonElement).textContent?.includes('Erneut versuchen')) as HTMLButtonElement;
    expect(retry).toBeDefined(); retry.click();
    await respond(`/api/v1/chats/${id}/messages`, { items: [message], before: null });
    await respond(`/api/v1/chats/${id}/read`, {});
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role=alert]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Found your keys');
    fixture.destroy();
  });

});

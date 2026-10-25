import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FinderEntry } from './finder-entry';
import { QR_READER } from '../qr-reader';

const id = '123e4567-e89b-42d3-a456-426614174000';

describe('Finder entry and local QR scanning', () => {
  const start = vi.fn(), destroy = vi.fn(), constructed = vi.fn(), scanImage = vi.fn();
  const loadReader = vi.fn();
  let decode: (result: { data: string }) => void;
  class Reader {
    static scanImage = scanImage;
    constructor(_video: HTMLVideoElement, onDecode: typeof decode) { decode = onDecode; constructed(); }
    start = start;
    destroy = destroy;
  }
  beforeEach(() => {
    TestBed.resetTestingModule(); localStorage.clear();
    for (const mock of [start, destroy, constructed, scanImage, loadReader]) mock.mockReset();
    start.mockResolvedValue(undefined); loadReader.mockResolvedValue(Reader);
    scanImage.mockResolvedValue({ data: id });
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideRouter([]),
      { provide: QR_READER, useValue: loadReader }] });
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  it('does not load the reader or access a camera until requested', async () => {
    const fixture = TestBed.createComponent(FinderEntry); await fixture.whenStable();
    expect(loadReader).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.camera').hidden).toBe(true);
  });

  it('validates manual IDs and navigates locally without sending an email', async () => {
    const fixture = TestBed.createComponent(FinderEntry); await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('#tag-id');
    input.value = 'https://foreign.example/f/' + id; input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#finder-error').textContent).toContain('gültige Tag-ID');
    expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
    input.value = id; input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/f', id]);
  });

  it('stops the camera after a valid QR result', async () => {
    const fixture = TestBed.createComponent(FinderEntry); await fixture.whenStable();
    fixture.nativeElement.querySelector('.scan-button').click(); await fixture.whenStable();
    expect(start).toHaveBeenCalledTimes(1);
    decode({ data: `${location.origin}/f/${id}` }); await fixture.whenStable();
    expect(destroy).toHaveBeenCalled();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/f', id]);
    expect(fixture.nativeElement.querySelector('.camera').hidden).toBe(true);
  });

  it('shows manual and image alternatives if camera permission or hardware is unavailable', async () => {
    start.mockRejectedValue(new Error('permission denied'));
    const fixture = TestBed.createComponent(FinderEntry); await fixture.whenStable();
    fixture.nativeElement.querySelector('.scan-button').click(); await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#finder-error').textContent).toContain('Kamerafreigabe');
    expect(destroy).toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('#tag-id')).not.toBeNull();
  });

  it('does not open a camera when the component is destroyed while the reader loads', async () => {
    let resolveReader!: (reader: typeof Reader) => void;
    loadReader.mockReturnValue(new Promise(resolve => { resolveReader = resolve; }));
    const fixture = TestBed.createComponent(FinderEntry); await fixture.whenStable();
    fixture.nativeElement.querySelector('.scan-button').click();
    fixture.destroy(); resolveReader(Reader); await Promise.resolve();
    expect(constructed).not.toHaveBeenCalled();
    expect(start).not.toHaveBeenCalled();
  });

  it('releases an active camera on component destruction', async () => {
    const fixture = TestBed.createComponent(FinderEntry); await fixture.whenStable();
    fixture.nativeElement.querySelector('.scan-button').click(); await fixture.whenStable();
    fixture.destroy(); expect(destroy).toHaveBeenCalledTimes(1);
  });

  it('decodes a selected image locally and navigates to its tag', async () => {
    const fixture = TestBed.createComponent(FinderEntry); await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('#qr-image');
    const file = new File(['image'], 'tag.png', { type: 'image/png' });
    Object.defineProperty(input, 'files', { value: [file] });
    input.dispatchEvent(new Event('change')); await fixture.whenStable();
    expect(scanImage).toHaveBeenCalledWith(file, { returnDetailedScanResult: true });
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/f', id]);
    expect(input.value).toBe('');
  });
});

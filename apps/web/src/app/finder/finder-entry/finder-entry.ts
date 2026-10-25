import { Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import type QrScanner from 'qr-scanner';
import { LanguageService } from '../../core/language.service';
import { publicTagId } from '../public-tag-id';
import { QR_READER } from '../qr-reader';

@Component({
  selector: 'app-finder-entry',
  templateUrl: './finder-entry.html',
  styleUrl: './finder-entry.scss',
})
export class FinderEntry {
  protected readonly language = inject(LanguageService);
  protected readonly tag = signal('');
  protected readonly scanning = signal(false);
  protected readonly readingImage = signal(false);
  protected readonly error = signal<'invalidTag' | 'cameraFailed' | 'imageFailed' | ''>('');
  private readonly router = inject(Router);
  private readonly loadReader = inject(QR_READER);
  private readonly destroyRef = inject(DestroyRef);
  private readonly preview = viewChild.required<ElementRef<HTMLVideoElement>>('preview');
  private scanner?: QrScanner;
  private requestVersion = 0;

  constructor() {
    this.destroyRef.onDestroy(() => this.closeScanner());
  }

  protected submitTag(event: Event): void {
    event.preventDefault();
    this.openTag(this.tag());
  }

  private openTag(value: string): void {
    if (this.destroyRef.destroyed) return;
    this.closeScanner();
    const id = publicTagId(value, location.origin);
    if (!id) { this.error.set('invalidTag'); return; }
    this.error.set('');
    void this.router.navigate(['/f', id]);
  }

  protected async startScanner(): Promise<void> {
    if (this.scanning() || this.readingImage()) return;
    this.closeScanner();
    this.error.set('');
    this.scanning.set(true);
    const version = this.requestVersion;
    try {
      const Reader = await this.loadReader();
      if (this.destroyRef.destroyed || version !== this.requestVersion) return;
      const scanner = new Reader(this.preview().nativeElement, result => this.openTag(result.data), {
        preferredCamera: 'environment', maxScansPerSecond: 5, highlightScanRegion: true, returnDetailedScanResult: true,
      });
      this.scanner = scanner;
      await scanner.start();
      if (this.destroyRef.destroyed || version !== this.requestVersion) scanner.destroy();
    } catch {
      if (this.destroyRef.destroyed || version !== this.requestVersion) return;
      this.closeScanner();
      this.error.set('cameraFailed');
    }
  }

  protected closeScanner(): void {
    this.requestVersion++;
    this.scanner?.destroy();
    this.scanner = undefined;
    this.scanning.set(false);
  }

  protected async scanImage(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.readingImage()) return;
    this.closeScanner();
    this.error.set('');
    if (!file.type.startsWith('image/') || file.size > 10_000_000) {
      this.error.set('imageFailed'); return;
    }
    this.readingImage.set(true);
    const version = this.requestVersion;
    try {
      const Reader = await this.loadReader();
      if (this.destroyRef.destroyed || version !== this.requestVersion) return;
      const result = await Reader.scanImage(file, { returnDetailedScanResult: true });
      if (!this.destroyRef.destroyed && version === this.requestVersion) this.openTag(result.data);
    } catch {
      if (!this.destroyRef.destroyed && version === this.requestVersion) this.error.set('imageFailed');
    } finally {
      if (!this.destroyRef.destroyed) this.readingImage.set(false);
    }
  }
}

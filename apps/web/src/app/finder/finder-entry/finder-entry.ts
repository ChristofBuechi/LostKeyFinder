import { Component, DestroyRef, ElementRef, Renderer2, inject, signal, viewChild } from '@angular/core';
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
  private readonly preview = viewChild.required<ElementRef<HTMLElement>>('preview');
  private readonly renderer = inject(Renderer2);
  private previewContainer?: HTMLElement;
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
      const scanner = new Reader(this.createPreview(), result => this.openTag(result.data), {
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

  private createPreview(): HTMLVideoElement {
    // Each reader owns its DOM: delayed cleanup must never touch a later stream.
    const container = this.renderer.createElement('div') as HTMLElement;
    const video = this.renderer.createElement('video') as HTMLVideoElement;
    this.renderer.setProperty(video, 'muted', true);
    this.renderer.setAttribute(video, 'playsinline', '');
    this.renderer.setAttribute(video, 'aria-label', this.language.copy().scanPreview);
    this.renderer.appendChild(container, video);
    this.renderer.appendChild(this.preview().nativeElement, container);
    this.previewContainer = container;
    return video;
  }

  protected closeScanner(): void {
    this.requestVersion++;
    this.scanner?.destroy();
    this.scanner = undefined;
    if (this.previewContainer?.parentNode) {
      this.renderer.removeChild(this.previewContainer.parentNode, this.previewContainer);
    }
    this.previewContainer = undefined;
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

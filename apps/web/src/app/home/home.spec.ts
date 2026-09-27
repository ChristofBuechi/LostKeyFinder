import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ApiConfiguration } from '@lost-key-finder/api-client';
import { signal } from '@angular/core';
import { beforeEach, afterEach, describe, expect, it } from 'vitest';
import { LanguageService } from '../core/language.service';
import { Home } from './home';

describe('Home', () => {
  let fixture: ComponentFixture<Home>;
  let http: HttpTestingController | undefined;

  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [Home],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ApiConfiguration, useValue: new ApiConfiguration() },
        {
          provide: LanguageService,
          useValue: {
            copy: signal({
              checking: 'Verbindung wird geprüft',
              available: 'Dienst verfügbar',
              unavailable: 'Dienst vorübergehend nicht verfügbar',
            }),
          },
        },
      ],
    });
    fixture = TestBed.createComponent(Home);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http?.verify());

  it('starts with a checking status and calls the versioned readiness endpoint', () => {
    expect(fixture.nativeElement.querySelector('.service-status').textContent.trim())
      .toContain('Verbindung wird geprüft');

    const request = http!.expectOne('/api/v1/health/ready');
    expect(request.request.method).toBe('GET');
  });

  it('shows the available status after a successful readiness response', () => {
    http!.expectOne('/api/v1/health/ready').flush({ status: 'ok' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.service-status').textContent.trim())
      .toContain('Dienst verfügbar');
  });

  it('shows the unavailable status after a readiness failure', () => {
    http!.expectOne('/api/v1/health/ready').flush(
      { title: 'Service unavailable' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.service-status').textContent.trim())
      .toContain('Dienst vorübergehend nicht verfügbar');
  });

  it('uses a polite live region for the service status', () => {
    expect(fixture.nativeElement.querySelector('.service-status').getAttribute('aria-live'))
      .toBe('polite');
    http!.expectOne('/api/v1/health/ready').flush({ status: 'ok' });
  });
});

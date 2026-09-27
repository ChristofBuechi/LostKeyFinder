import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  it('creates the application shell', () => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(fixture.componentInstance).toBeInstanceOf(App);
    expect(fixture.nativeElement.querySelector('.skip-link').getAttribute('href'))
      .toBe('#main-content');
    expect(fixture.nativeElement.querySelector('main#main-content').getAttribute('tabindex'))
      .toBe('-1');
    expect(fixture.nativeElement.querySelector('.language-toggle').getAttribute('aria-label'))
      .toContain('Auf Englisch wechseln');
  });
});

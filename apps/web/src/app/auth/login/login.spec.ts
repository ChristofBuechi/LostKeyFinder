import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Auth } from '../auth';
import { LanguageService } from '../../core/language.service';
import { Login } from './login';

describe('Owner login form', () => {
  const sendLink = vi.fn();
  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
    sendLink.mockReset().mockResolvedValue(undefined);
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideRouter([]),
      { provide: Auth, useValue: { configured: true, signedIn: signal(false), sendLink } }] });
  });

  it('shows an associated error after submission and never sends an invalid email', async () => {
    const fixture = TestBed.createComponent(Login);
    await fixture.whenStable();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('input');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toContain('email-error');
    expect(fixture.nativeElement.querySelector('#email-error').textContent).toContain('gültige E-Mail');
    expect(sendLink).not.toHaveBeenCalled();
  });

  it('sends the entered email and announces success in German and English', async () => {
    const fixture = TestBed.createComponent(Login);
    await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('input');
    input.value = 'owner@example.com';
    input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
    expect(sendLink).toHaveBeenCalledWith('owner@example.com');
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Prüfe dein Postfach');
    TestBed.inject(LanguageService).toggle();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('Check your inbox');
  });
});

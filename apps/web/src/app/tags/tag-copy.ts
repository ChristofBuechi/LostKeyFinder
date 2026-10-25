import { Injectable, computed, inject } from '@angular/core';
import { LanguageService } from '../core/language.service';

@Injectable({ providedIn: 'root' })
export class TagCopy {
  private readonly language = inject(LanguageService);
  readonly text = computed(() => this.language.language() === 'de' ? {
    mine: 'Meine Tags', admin: 'Tag-Bestand', account: 'Mein Konto', adminLink: 'Administration',
    intro: 'Erstelle einen eigenen Tag und lade den QR-Code herunter.',
    adminIntro: 'Alle erzeugten Tags dieser Umgebung, unabhängig von Besitzer und Herkunft.',
    create: 'Neuen Tag erstellen', label: 'Bezeichnung (optional)',
    creating: 'Tag wird erstellt …', loading: 'Tags werden geladen …', empty: 'Keine Tags gefunden.',
    failed: 'Die Anfrage konnte nicht abgeschlossen werden. Bitte versuche es erneut.',
    denied: 'Für diese Ansicht fehlt die Berechtigung oder dein Konto ist gesperrt.',
    expired: 'Bitte melde dich erneut an.', retry: 'Erneut versuchen',
    pending: 'Die Erstellung ist noch nicht bestätigt. Ein erneuter Versuch verwendet denselben Auftrag.',
    filter: 'Filter anwenden', all: 'Alle', search: 'Öffentliche Tag-ID', origin: 'Herkunft', status: 'Status',
    assignment: 'Zuordnung', assigned: 'Zugewiesen', free: 'Unzugewiesen',
    SELF_CREATED: 'Selbst erstellt', PREPRODUCED: 'Vorproduziert',
    ACTIVE: 'Aktiv', INACTIVE: 'Inaktiv', BLOCKED: 'Gesperrt', RETIRED: 'Stillgelegt',
    PREPARED: 'Vorbereitet', RELEASED: 'Freigegeben', inventory: 'Produktion', batch: 'Serie',
    created: 'Erstellt', owner: 'Besitzerreferenz', actions: 'Aktionen', qr: 'QR-Code herunterladen',
    deactivate: 'Deaktivieren', activate: 'Aktivieren', first: 'Zur ersten Seite', next: 'Nächste Seite',
    publicLink: 'Öffentlicher Tag-Link', note: 'Fundmeldung und Kontaktaufnahme werden später ergänzt.',
    table: 'Tags, neueste zuerst', invalid: 'Bitte gib eine gültige UUID-v4 ein.',
  } : {
    mine: 'My tags', admin: 'Tag inventory', account: 'My account', adminLink: 'Administration',
    intro: 'Create your own tag and download its QR code.',
    adminIntro: 'All tags in this environment, regardless of owner and origin.',
    create: 'Create a new tag', label: 'Label (optional)',
    creating: 'Creating tag …', loading: 'Loading tags …', empty: 'No tags found.',
    failed: 'The request could not be completed. Please try again.',
    denied: 'You do not have access to this view or your account is blocked.',
    expired: 'Please sign in again.', retry: 'Try again',
    pending: 'Creation has not been confirmed yet. Retrying uses the same request.',
    filter: 'Apply filters', all: 'All', search: 'Public tag ID', origin: 'Origin', status: 'Status',
    assignment: 'Assignment', assigned: 'Assigned', free: 'Unassigned',
    SELF_CREATED: 'Self-created', PREPRODUCED: 'Preproduced',
    ACTIVE: 'Active', INACTIVE: 'Inactive', BLOCKED: 'Blocked', RETIRED: 'Retired',
    PREPARED: 'Prepared', RELEASED: 'Released', inventory: 'Production', batch: 'Batch',
    created: 'Created', owner: 'Owner reference', actions: 'Actions', qr: 'Download QR code',
    deactivate: 'Deactivate', activate: 'Activate', first: 'Back to first page', next: 'Next page',
    publicLink: 'Public tag link', note: 'Found-item reports and contact will be added later.',
    table: 'Tags, newest first', invalid: 'Please enter a valid UUID-v4.',
  });
}

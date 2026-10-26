import { Injectable, computed, inject } from '@angular/core';
import { LanguageService } from '../core/language.service';

@Injectable({ providedIn: 'root' })
export class ChatCopy {
  private readonly language = inject(LanguageService);
  readonly text = computed(() => this.language.language() === 'de' ? {
    title: 'Nachrichten', newest: 'Neueste Nachrichten', you: 'Du', other: 'Chatteilnehmer', report: 'Fund melden', intro: 'Hinterlasse dem Besitzer eine private Nachricht. Deine E-Mail-Adresse bleibt verborgen.',
    message: 'Nachricht', image: 'Bild (optional)', imageHint: 'JPEG oder PNG, maximal 5 MB und 2048 × 2048 Pixel. Beschreibe wichtige Bildinhalte im Text.',
    send: 'Senden', sending: 'Wird gesendet …', confirm: 'E-Mail bestätigen und Fund melden',
    error: 'Das hat nicht geklappt. Bitte prüfe deine Eingaben und versuche es erneut.', retry: 'Erneut versuchen',
    loading: 'Wird geladen …', empty: 'Noch keine Nachrichten.', closed: 'Dieser Chat ist schreibgeschützt.',
    older: 'Ältere Nachrichten laden', close: 'Rückgabe abschliessen', closeHint: 'Damit werden alle Chats dieses Fundfalls schreibgeschützt.',
    cancel: 'Abbrechen', closeConfirm: 'Abschluss bestätigen', back: 'Zur Chatübersicht',
    unread: 'ungelesen', previous: 'Zurück', next: 'Weiter', page: 'Seite', of: 'von',
    attachment: 'Angehängtes Bild', showImage: 'Bild laden', noImage: 'Bild entfernen', signIn: 'Zum Lesen mit deiner E-Mail-Adresse anmelden',
    owner: 'Besitzer', finder: 'Finder', completed: 'Fundmeldung wird bestätigt …',
  } : {
    title: 'Messages', newest: 'Latest messages', you: 'You', other: 'Chat participant', report: 'Report found item', intro: 'Leave a private message for the owner. Your email address stays private.',
    message: 'Message', image: 'Image (optional)', imageHint: 'JPEG or PNG, up to 5 MB and 2048 × 2048 pixels. Describe important image content in the text.',
    send: 'Send', sending: 'Sending …', confirm: 'Verify email and report item',
    error: 'Something went wrong. Check your input and try again.', retry: 'Try again',
    loading: 'Loading …', empty: 'No messages yet.', closed: 'This chat is read-only.',
    older: 'Load older messages', close: 'Complete return', closeHint: 'This makes all chats in this case read-only.',
    cancel: 'Cancel', closeConfirm: 'Confirm completion', back: 'Back to messages',
    unread: 'unread', previous: 'Previous', next: 'Next', page: 'Page', of: 'of',
    attachment: 'Attached image', showImage: 'Load image', noImage: 'Remove image', signIn: 'Sign in with your email address to read this chat',
    owner: 'Owner', finder: 'Finder', completed: 'Confirming report …',
  });
}

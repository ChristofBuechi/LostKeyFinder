import { TranslocoPipe } from '@jsverse/transloco';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { HealthService } from '@lost-key-finder/api-client';
import { catchError, map, of, startWith } from 'rxjs';
import { HomeEntry } from './home-entry/home-entry';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [TranslocoPipe, HomeEntry],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  protected readonly apiStatus = toSignal(
    inject(HealthService).healthControllerReady().pipe(
      map(() => 'available' as const),
      catchError(() => of('unavailable' as const)),
      startWith('checking' as const),
    ),
    { requireSync: true },
  );
}

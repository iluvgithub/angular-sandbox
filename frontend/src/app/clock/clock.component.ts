import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription, firstValueFrom, of, timer } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';

// How often the Clock screen polls the backend for the current time.
const POLL_INTERVAL_MS = 15_000;

// Always use an absolute (leading-slash) path here: a relative one resolves
// against the *current route's* URL, not the site root, so it would break
// depending on which page you're viewing it from.
const CLOCK_NOW_URL = '/callclock';

@Component({
  selector: 'app-clock',
  standalone: true,
  templateUrl: './clock.component.html',
  styleUrl: './clock.component.css',
})
export class ClockComponent implements OnInit, OnDestroy {
  // Signal holding the latest formatted timestamp fetched from the server.
  readonly now = signal('----:--:-- --:--:--');
  readonly connected = signal(false);
  readonly refreshing = signal(false);

  private pollSubscription?: Subscription;

  constructor(private readonly http: HttpClient) {}

  ngOnInit(): void {
    // timer(0, interval) fires immediately, then every POLL_INTERVAL_MS.
    // switchMap cancels any in-flight request if a new tick arrives before
    // it resolves, so slow responses can't pile up.
    this.pollSubscription = timer(0, POLL_INTERVAL_MS)
      .pipe(
        switchMap(() =>
          this.http.get(CLOCK_NOW_URL, { responseType: 'text' }).pipe(
            // Catch per-request errors here (not around the whole timer),
            // so one failed poll doesn't kill all future polling.
            catchError(() => of(null))
          )
        )
      )
      .subscribe((value) => {
        if (value !== null) {
          this.now.set(value);
          this.connected.set(true);
        } else {
          this.connected.set(false);
        }
      });
  }

  ngOnDestroy(): void {
    this.pollSubscription?.unsubscribe();
  }

  // On-demand fetch outside the regular poll cadence - handy right after an
  // action where you don't want to wait for the next scheduled tick.
  async forceRefresh(): Promise<void> {
    this.refreshing.set(true);
    try {
      const result = await firstValueFrom(
        this.http.get(CLOCK_NOW_URL, { responseType: 'text' })
      );
      this.now.set(result);
      this.connected.set(true);
    } catch {
      this.connected.set(false);
    } finally {
      this.refreshing.set(false);
    }
  }
}

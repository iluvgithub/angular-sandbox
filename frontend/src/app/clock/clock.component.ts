import {Component, OnDestroy, OnInit, signal} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom} from 'rxjs';

@Component({
    selector: 'app-clock',
    standalone: true,
    templateUrl: './clock.component.html',
    styleUrl: './clock.component.css',
})
export class ClockComponent implements OnInit, OnDestroy {
    // Signal holding the latest formatted timestamp pushed by the server.
    readonly now = signal('----:--:-- --:--:--');
    readonly connected = signal(false);
    readonly refreshing = signal(false);

    private source?: EventSource;

    constructor(private readonly http: HttpClient) {
    }

    ngOnInit(): void {
        // Same-origin SSE: served by the same http4s app as everything else.
        // In local dev, ng serve proxies /sse/* to the backend (see proxy.conf.json).
        // EventSource reconnects on its own if the connection drops - no manual
        // retry logic needed here, unlike a raw WebSocket.
        const source = new EventSource('/sse/clock');
        this.source = source;
        source.onopen = () => this.connected.set(true);
        source.onmessage = (event: MessageEvent<string>) => {
                console.log('SSE:', event.data);
                this.now.set(event.data);
            }
        source.onerror = (error: Event)  =>
        {
            this.connected.set(false);
            console.error('SSE error:', error)    ;
        };
    }

    ngOnDestroy(): void {
        this.source?.close();
    }

    async forceRefresh(): Promise<void> {
        this.refreshing.set(true);
        try {
            const urlToCall = "debugclock"
            const result = await firstValueFrom(
                this.http.get(urlToCall, {responseType: 'text'})
            );

            this.now.set(result)
            //this.now.set(result)
        } catch {
            // The SSE stream's own onerror handler already reflects connectivity
            // issues via `connected`; nothing extra to show here.
        } finally {
            this.refreshing.set(false);
        }
    }
}

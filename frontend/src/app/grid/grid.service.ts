import {Injectable, NgZone} from '@angular/core';
import {Observable} from 'rxjs';

export interface Cell {
    i: number;
    j: number;
    value: number;
}

@Injectable({providedIn: 'root'})
export class GridService {
    constructor(private zone: NgZone) {
    }

    /** Opens a Server-Sent Events (SSE) connection to /api/stream?.... and emits each
     * decoded Cell as it arrives. Reconnection on transient errors is left
     * to the browser's built-in EventSource retry behavior.
     */
    streamUpdates(rows: number, cols: number): Observable<Cell> {
        return new Observable<Cell>((observer) => {
            const params = new URLSearchParams({
                rows: String(rows),
                cols: String(cols),
            });
            const eventSource = new EventSource(`/api/stream?${params.toString()}`);

            eventSource.onmessage = (event: MessageEvent) => {
                this.zone.run(() => {
                    try {
                        const cell = JSON.parse(event.data) as Cell;
                        observer.next(cell);
                    } catch (err) {
                        console.error('Failed to parse SSE payload', err);
                    }
                });
            };

            eventSource.onerror = (err) => {
                // EventSource auto-retries; just surface the error for logging.
                this.zone.run(() => console.warn('SSE connection issue', err));
            };

            return () => eventSource.close();
        });
    }

}

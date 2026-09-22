import { Injectable, NgZone } from '@angular/core';
import { Observable } from 'rxjs';

export interface CellUpdate {
  row: number;
  col: number;
  value: number;
  timestamp: number;
}

export interface GridSnapshotDto {
  rows: number;
  cols: number;
  values: number[][];
}

@Injectable({ providedIn: 'root' })
export class GridDataService {
  private readonly baseUrl = 'http://localhost:8080/api/grid';

  constructor(private zone: NgZone) {}

  /** One-off fetch of the current grid state, used to render the initial layout. */
  fetchInitialState(): Promise<GridSnapshotDto> {
    return fetch(`${this.baseUrl}/state`).then((res) => {
      if (!res.ok) {
        throw new Error(`Failed to load grid state: ${res.status}`);
      }
      return res.json() as Promise<GridSnapshotDto>;
    });
  }

  /**
   * Live feed of cell changes via Server-Sent Events. EventSource runs
   * outside Angular's zone, so callbacks are re-entered into the zone
   * explicitly to keep change detection working.
   */
  streamUpdates(): Observable<CellUpdate> {
    return new Observable<CellUpdate>((subscriber) => {
      const source = new EventSource(`${this.baseUrl}/stream`);

      source.onmessage = (event: MessageEvent<string>) => {
        this.zone.run(() => {
          try {
            const parsed = JSON.parse(event.data) as CellUpdate;
            subscriber.next(parsed);
          } catch (err) {
            console.error('Could not parse grid update payload', err);
          }
        });
      };

      source.onerror = (err) => {
        // EventSource retries automatically; just surface the error for logging.
        this.zone.run(() => console.warn('Grid stream connection issue', err));
      };

      return () => source.close();
    });
  }
}

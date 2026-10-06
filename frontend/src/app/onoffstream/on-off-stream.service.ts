import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, of} from 'rxjs';
import { switchMap, catchError, map } from 'rxjs/operators';
import { IsOnResponse } from './is-on-response.model';
@Injectable({providedIn: 'root'})
export class OnOffStreamService {
    private readonly STREAM_URL = '/api/onoffstream';
    private readonly ON_URL = '/api/onstream';
    private readonly OFF_URL = '/api/offstream';
    private readonly IS_ON_URL = '/api/ison';
    constructor(private http: HttpClient) {
    }

    /** Polls the stream endpoint and emits each payload */
    getStream(): Observable<any> {
        return new Observable((observer) => {
            const eventSource = new EventSource(this.STREAM_URL);
            eventSource.onmessage = (event) => observer.next(JSON.parse(event.data));
            eventSource.onerror = (err) => observer.error(err);
            return () => eventSource.close();
        });
    }
    getIsOn(): Observable<boolean> {
        return this.http.get<IsOnResponse>(this.IS_ON_URL).pipe(
            map((response) => response.status === 'on'),
            catchError((err) => {
                console.error('Failed to fetch initial on/off state:', err);
                return of(false); // safe default: assume off if the check fails
            })
        );
    }

    turnOn(): Observable<any> {
        return this.http.get(this.ON_URL);
    }

    turnOff(): Observable<any> {
        return this.http.get(this.OFF_URL);
    }
}
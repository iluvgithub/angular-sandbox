import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

@Injectable({providedIn: 'root'})
export class OnOffStreamService {
    private readonly STREAM_URL = '/api/onoffstream';
    private readonly ON_URL = '/api/onstream';
    private readonly OFF_URL = '/api/offstream';

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

    turnOn(): Observable<any> {
        return this.http.get(this.ON_URL);
    }

    turnOff(): Observable<any> {
        return this.http.get(this.OFF_URL);
    }
}

import { HttpClient } from '@angular/common/http';
import { Injectable, NgZone } from '@angular/core';
import { Observable } from 'rxjs';
export interface ChatEvent {
    sender: string;
    text: string;
}

export interface SendResponse {
    status: string;
}


@Injectable({ providedIn: 'root' })
export class ChatService {
    constructor(private http: HttpClient, private zone: NgZone) {}

    /** Opens an SSE connection to /api/chat/{room}/stream and emits each
     * message text as it's broadcast to that room.
     */

    streamRoom(room: string): Observable<ChatEvent> {
        return new Observable<ChatEvent>((observer) => {
            const eventSource = new EventSource(`/api/chat/${encodeURIComponent(room)}/stream`);

            eventSource.onmessage = (event: MessageEvent) => {
                this.zone.run(() => {
                    try {
                        observer.next(JSON.parse(event.data) as ChatEvent);
                    } catch (err) {
                        console.error('Failed to parse chat SSE payload', err);
                    }
                });
            };

            eventSource.onerror = (err) => {
                this.zone.run(() => console.warn('Chat SSE connection issue', err));
            };

            return () => eventSource.close();
        });
    }

    send(room: string, sender: string, text: string): Observable<SendResponse> {
        return this.http.post<SendResponse>('/api/chat/send', { room, sender, text });
    }

}


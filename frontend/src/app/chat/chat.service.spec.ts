import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ChatService, ChatEvent, SendResponse } from './chat.service';

// Minimal fake EventSource so we control when/what SSE messages arrive,
// without opening a real network connection.
class MockEventSource {
    static instances: MockEventSource[] = [];
    onmessage: ((event: MessageEvent) => void) | null = null;
    onerror: ((event: Event) => void) | null = null;
    closed = false;

    constructor(public url: string) {
        MockEventSource.instances.push(this);
    }

    close(): void {
        this.closed = true;
    }

    // Test helper to simulate the server pushing a message
    emit(data: unknown): void {
        this.onmessage?.({ data: JSON.stringify(data) } as MessageEvent);
    }

    emitRawError(): void {
        this.onerror?.(new Event('error'));
    }
}

describe('ChatService', () => {
    let service: ChatService;
    let httpMock: HttpTestingController;
    let originalEventSource: typeof EventSource;

    beforeEach(() => {
        originalEventSource = (window as any).EventSource;
        (window as any).EventSource = MockEventSource;
        MockEventSource.instances = [];

        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [ChatService]
        });

        service = TestBed.inject(ChatService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        (window as any).EventSource = originalEventSource;
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('send()', () => {
        it('should POST to /api/chat/send with room, sender and text', () => {
            const mockResponse: SendResponse = { status: 'ok' };

            service.send('lobby', 'client-123', 'hello world').subscribe((response) => {
                expect(response).toEqual(mockResponse);
            });

            const req = httpMock.expectOne('/api/chat/send');
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual({ room: 'lobby', sender: 'client-123', text: 'hello world' });

            req.flush(mockResponse);
        });
    });

    describe('streamRoom()', () => {
        it('should open an EventSource to the correct URL', () => {
            const sub = service.streamRoom('my room').subscribe();

            expect(MockEventSource.instances.length).toBe(1);
            expect(MockEventSource.instances[0].url).toBe('/api/chat/my%20room/stream');

            sub.unsubscribe();
        });

        it('should emit parsed ChatEvent objects as SSE messages arrive', () => {
            const received: ChatEvent[] = [];
            const sub = service.streamRoom('lobby').subscribe((event) => received.push(event));

            const mockSource = MockEventSource.instances[0];
            mockSource.emit({ sender: 'alice', text: 'hi there' });
            mockSource.emit({ sender: 'bob', text: 'hello!' });

            expect(received).toEqual([
                { sender: 'alice', text: 'hi there' },
                { sender: 'bob', text: 'hello!' }
            ]);

            sub.unsubscribe();
        });

        it('should not emit and should log an error when SSE payload is malformed JSON', () => {
            const received: ChatEvent[] = [];
            spyOn(console, 'error');
            const sub = service.streamRoom('lobby').subscribe((event) => received.push(event));

            const mockSource = MockEventSource.instances[0];
            mockSource.onmessage?.({ data: 'not valid json' } as MessageEvent);

            expect(received.length).toBe(0);
            expect(console.error).toHaveBeenCalled();

            sub.unsubscribe();
        });

        it('should close the EventSource on unsubscribe', () => {
            const sub = service.streamRoom('lobby').subscribe();
            const mockSource = MockEventSource.instances[0];

            expect(mockSource.closed).toBeFalse();
            sub.unsubscribe();
            expect(mockSource.closed).toBeTrue();
        });
    });
});
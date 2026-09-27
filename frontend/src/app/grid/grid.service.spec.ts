import { TestBed } from '@angular/core/testing';
import { GridService, Cell } from './grid.service';

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

    emitError(): void {
        this.onerror?.(new Event('error'));
    }
}

describe('GridService', () => {
    let service: GridService;
    let originalEventSource: typeof EventSource;

    beforeEach(() => {
        originalEventSource = (window as any).EventSource;
        (window as any).EventSource = MockEventSource;
        MockEventSource.instances = [];

        TestBed.configureTestingModule({
            providers: [GridService]
        });

        service = TestBed.inject(GridService);
    });

    afterEach(() => {
        (window as any).EventSource = originalEventSource;
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should open an EventSource with rows and cols as query params', () => {
        const sub = service.streamUpdates(10, 20).subscribe();

        expect(MockEventSource.instances.length).toBe(1);
        expect(MockEventSource.instances[0].url).toBe('/api/stream?rows=10&cols=20');

        sub.unsubscribe();
    });

    it('should emit parsed Cell objects as SSE messages arrive', () => {
        const received: Cell[] = [];
        const sub = service.streamUpdates(5, 5).subscribe((cell) => received.push(cell));

        const mockSource = MockEventSource.instances[0];
        mockSource.emit({ i: 0, j: 0, value: 42 });
        mockSource.emit({ i: 1, j: 2, value: 7 });

        expect(received).toEqual([
            { i: 0, j: 0, value: 42 },
            { i: 1, j: 2, value: 7 }
        ]);

        sub.unsubscribe();
    });

    it('should not emit and should log an error when the SSE payload is malformed JSON', () => {
        const received: Cell[] = [];
        spyOn(console, 'error');
        const sub = service.streamUpdates(5, 5).subscribe((cell) => received.push(cell));

        const mockSource = MockEventSource.instances[0];
        mockSource.onmessage?.({ data: 'not valid json' } as MessageEvent);

        expect(received.length).toBe(0);
        expect(console.error).toHaveBeenCalled();

        sub.unsubscribe();
    });

    it('should log a warning on connection error without erroring the observable', () => {
        spyOn(console, 'warn');
        let errored = false;
        const sub = service.streamUpdates(5, 5).subscribe({
            error: () => (errored = true)
        });

        const mockSource = MockEventSource.instances[0];
        mockSource.emitError();

        expect(console.warn).toHaveBeenCalled();
        expect(errored).toBeFalse(); // onerror never calls observer.error(), so the stream stays alive

        sub.unsubscribe();
    });

    it('should close the EventSource on unsubscribe', () => {
        const sub = service.streamUpdates(5, 5).subscribe();
        const mockSource = MockEventSource.instances[0];

        expect(mockSource.closed).toBeFalse();
        sub.unsubscribe();
        expect(mockSource.closed).toBeTrue();
    });

    it('should open a separate EventSource per subscription', () => {
        const sub1 = service.streamUpdates(1, 1).subscribe();
        const sub2 = service.streamUpdates(2, 2).subscribe();

        expect(MockEventSource.instances.length).toBe(2);
        expect(MockEventSource.instances[0].url).toBe('/api/stream?rows=1&cols=1');
        expect(MockEventSource.instances[1].url).toBe('/api/stream?rows=2&cols=2');

        sub1.unsubscribe();
        sub2.unsubscribe();
    });
});
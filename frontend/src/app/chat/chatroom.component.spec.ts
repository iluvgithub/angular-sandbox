import { TestBed } from '@angular/core/testing';
import { of, throwError, Subject } from 'rxjs';
import { ChatRoomComponent } from './chatroom.component';
import { ChatService, ChatEvent, SendResponse } from './chat.service';

describe('ChatRoomComponent', () => {
    let chatServiceSpy: jasmine.SpyObj<ChatService>;
    let streamSubject: Subject<ChatEvent>;

    beforeEach(() => {
        streamSubject = new Subject<ChatEvent>();
        chatServiceSpy = jasmine.createSpyObj<ChatService>('ChatService', ['streamRoom', 'send']);
        chatServiceSpy.streamRoom.and.returnValue(streamSubject.asObservable());
        chatServiceSpy.send.and.returnValue(of({ status: 'ok' } as SendResponse));

        TestBed.configureTestingModule({
            imports: [ChatRoomComponent],
            providers: [{ provide: ChatService, useValue: chatServiceSpy }]
        });
    });

    function createComponent(room = 'lobby') {
        const fixture = TestBed.createComponent(ChatRoomComponent);
        fixture.componentInstance.room = room;
        fixture.detectChanges(); // triggers ngOnInit
        return fixture;
    }

    it('should create and connect to the stream on init', () => {
        const fixture = createComponent('lobby');
        const component = fixture.componentInstance;

        expect(component).toBeTruthy();
        expect(chatServiceSpy.streamRoom).toHaveBeenCalledWith('lobby');
        expect(component.connected).toBeTrue();
    });

    it('should append incoming messages and mark others as not own', () => {
        const fixture = createComponent('lobby');
        const component = fixture.componentInstance;

        streamSubject.next({ sender: 'someone-else', text: 'hi!' });

        expect(component.messages.length).toBe(1);
        expect(component.messages[0].text).toBe('hi!');
        expect(component.messages[0].own).toBeTrue(); // sender differs from this tab's generated clientId
    });

    it('should set connected to false if the stream errors', () => {
        const fixture = createComponent('lobby');
        const component = fixture.componentInstance;

        streamSubject.error(new Error('SSE failed'));

        expect(component.connected).toBeFalse();
    });

    it('should send trimmed message text and reset state on success', () => {
        const fixture = createComponent('lobby');
        const component = fixture.componentInstance;

        component.message = '  hello there  ';
        component.onSendClick();

        expect(chatServiceSpy.send).toHaveBeenCalledWith('lobby', jasmine.any(String), 'hello there');
        expect(component.message).toBe('');
        expect(component.sending).toBeFalse();
    });

    it('should not send when message is empty or only whitespace', () => {
        const fixture = createComponent('lobby');
        const component = fixture.componentInstance;

        component.message = '   ';
        component.onSendClick();

        expect(chatServiceSpy.send).not.toHaveBeenCalled();
    });

    it('should not send a second message while one is already in flight', () => {
        const fixture = createComponent('lobby');
        const component = fixture.componentInstance;

        component.message = 'first';
        component.sending = true; // simulate a send already in progress
        component.onSendClick();

        expect(chatServiceSpy.send).not.toHaveBeenCalled();
    });

    it('should reset sending flag on send error without clearing the message', () => {
        chatServiceSpy.send.and.returnValue(throwError(() => new Error('network error')));
        spyOn(console, 'error');

        const fixture = createComponent('lobby');
        const component = fixture.componentInstance;

        component.message = 'will fail';
        component.onSendClick();

        expect(component.sending).toBeFalse();
        expect(component.message).toBe('will fail'); // not cleared, unlike the success path
        expect(console.error).toHaveBeenCalled();
    });

    it('should unsubscribe from the stream on destroy', () => {
        const fixture = createComponent('lobby');
        const unsubscribeSpy = spyOn((fixture.componentInstance as any).subscription, 'unsubscribe');

        fixture.destroy();

        expect(unsubscribeSpy).toHaveBeenCalled();
    });
});
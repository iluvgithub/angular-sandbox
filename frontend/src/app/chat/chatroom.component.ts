import {Component, ElementRef, Input, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Subscription} from 'rxjs';
import {ChatEvent, ChatService} from './chat.service';
interface DisplayMessage {
    text: string;
    own: boolean;
}

@Component({
    selector: 'app-chatroom',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './chatroom.component.html',
    styleUrls: ['./chatroom.component.css'],
})
export class ChatRoomComponent implements OnInit, OnDestroy {
    // Bound from the route's `data: { room: '...' }` via withComponentInputBinding()
    @Input() room = '';

    message = '';
    messages: DisplayMessage[] = [];
    connected = false;
    sending = false;

    @ViewChild('messagesEl') private messagesEl?: ElementRef<HTMLDivElement>;

    // Identifies this browser tab so we can tell "my" messages apart from
    // everyone else's when they come back over the shared SSE broadcast.
    private readonly clientId = ChatRoomComponent.newClientId();

    private subscription?: Subscription;

    constructor(private chatService: ChatService) {
    }

    ngOnInit(): void {
        this.connectStream();
    }

    ngOnDestroy(): void {
        this.subscription?.unsubscribe();
    }

    onSendClick(): void {
        const text = this.message.trim();
        if (!text || this.sending) return;

        this.sending = true;
        this.chatService.send(this.room, this.clientId, text).subscribe({
            next: () => {
                this.message = '';
                this.sending = false;
            },
            error: (err) => {
                console.error('Failed to send chat message', err);
                this.sending = false;
            },
        });
    }
    private connectStream(): void {
        this.subscription?.unsubscribe();
        this.subscription = this.chatService.streamRoom(this.room).subscribe({
            next: (event: ChatEvent) => {
                this.messages.push({ text: event.text, own: event.sender === this.clientId });
                this.scrollToBottomSoon();
            },
            error: () => (this.connected = false),
        });
        this.connected = true;
    }



    private scrollToBottomSoon(): void {
        setTimeout(() => {
            const el = this.messagesEl?.nativeElement;
            if (el) el.scrollTop = el.scrollHeight;
        });
    }

    private static newClientId(): string {
        if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
            return crypto.randomUUID();
        }
        return `client-${Math.random().toString(36).slice(2)}`;
    }

}

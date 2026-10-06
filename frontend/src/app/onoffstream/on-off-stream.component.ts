import { Component, OnInit, OnDestroy , NgZone, ChangeDetectorRef} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { OnOffStreamService } from './on-off-stream.service';
import { OnOffStreamData, isOnOffStreamData } from './on-off-stream-data.model';

@Component({
    selector: 'app-on-off-stream',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './on-off-stream.component.html',
    styleUrl: './on-off-stream.component.scss'
})
export class OnOffStreamComponent implements OnInit, OnDestroy {
    streamText: string = '';
    private streamSub?: Subscription;
    isStreaming: boolean = true;


    constructor(private onOffStreamService: OnOffStreamService,
                private ngZone: NgZone,
                private cdr: ChangeDetectorRef) {}
    ngOnInit(): void {
         try {
             this.streamSub = this.onOffStreamService.getStream().subscribe((data: OnOffStreamData | null) => {
                 this.ngZone.run(() => {
                     if (data) {
                         this.streamText += JSON.stringify(data) + '\n';
                     }
                     this.cdr.detectChanges(); // force a refresh if needed
                 });
             });
        } catch (err) {
            console.error('Failed to initialize stream subscription:', err);
        }
    }

    private handleData(data: OnOffStreamData | null): void {
        console.info('debug here' + data);
        if (data) {
            const s = JSON.stringify(data)
            console.info('debug there' + s);

            this.streamText += s + '\n';
        } else {
            console.info('ELSE there — fetch failed or malformed payload');
        }
    }

    onOn(): void {
        this.onOffStreamService.turnOn().subscribe({
            next: () => this.isStreaming = true,
            error: (err) => console.error('Failed to turn on:', err)
        });
    }

    onOff(): void {
        this.onOffStreamService.turnOff().subscribe({
            next: () => {
                this.isStreaming = false;
            },
            error: (err) => console.error('Failed to turn off:', err)
        });
    }

    ngOnDestroy(): void {
        this.streamSub?.unsubscribe();
    }
}
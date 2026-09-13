import {
  Component,
  ElementRef,
  ViewChild,
  AfterViewInit,
  OnDestroy,
  HostListener,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

type Status = 'idle' | 'loading' | 'error';

@Component({
  selector: 'app-uppercase',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './uppercase.component.html',
  styleUrl: './uppercase.component.css',
})
export class UppercaseComponent implements AfterViewInit, OnDestroy {
  @ViewChild('plate', { static: true }) plateRef!: ElementRef<HTMLCanvasElement>;

  value = '';
  status: Status = 'idle';
  errorMessage = '';
  validationMessage = '';
  liveResult = ''; // mirrors the canvas content for screen readers

  private ctx!: CanvasRenderingContext2D;
  private dpr = 1;
  private lastPlateText = 'READY';
  private resizeTimer: ReturnType<typeof setTimeout> | undefined;
  private animationFrame: number | undefined;
  private readonly prefersReducedMotion =
    typeof window !== 'undefined' &&
    window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

  // Same-origin API: served by the same http4s app that serves this page.
  // In local dev, ng serve proxies /uppercase to the backend (see proxy.conf.json).
  private readonly apiBase = '/uppercase';

  constructor(private readonly http: HttpClient) {}

  ngAfterViewInit(): void {
    this.resizeCanvas();
    this.paintPlate(this.lastPlateText, { animate: false });
  }

  ngOnDestroy(): void {
    if (this.animationFrame !== undefined) cancelAnimationFrame(this.animationFrame);
    if (this.resizeTimer !== undefined) clearTimeout(this.resizeTimer);
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    // Debounce: resizing rebuilds the pixel buffer, which is relatively costly.
    clearTimeout(this.resizeTimer);
    this.resizeTimer = setTimeout(() => {
      this.resizeCanvas();
      this.paintPlate(this.lastPlateText, { animate: false });
    }, 120);
  }

  async convert(): Promise<void> {
    const input = this.value.trim();

    if (!input) {
      this.validationMessage = 'Type something first.';
      return;
    }
    this.validationMessage = '';
    this.status = 'loading';
    this.errorMessage = '';

    try {
      const result = await firstValueFrom(
        this.http.get(`${this.apiBase}/${encodeURIComponent(input)}`, { responseType: 'text' })
      );
      this.status = 'idle';
      this.paintPlate(result, { animate: true });
    } catch {
      this.status = 'error';
      this.errorMessage = "Couldn't reach the uppercase service. Try again.";
    }
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter') {
      this.convert();
    }
  }

  onInput(): void {
    if (this.validationMessage) this.validationMessage = '';
    if (this.status === 'error') this.status = 'idle';
  }

  // --- Canvas rendering --------------------------------------------------

  private resizeCanvas(): void {
    const canvas = this.plateRef.nativeElement;
    this.dpr = window.devicePixelRatio || 1;
    const cssWidth = canvas.clientWidth;
    const cssHeight = canvas.clientHeight;
    canvas.width = Math.round(cssWidth * this.dpr);
    canvas.height = Math.round(cssHeight * this.dpr);
    this.ctx = canvas.getContext('2d')!;
    this.ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
  }

  private paintPlate(text: string, opts: { animate: boolean }): void {
    this.lastPlateText = text;
    this.liveResult = text;

    if (this.animationFrame !== undefined) {
      cancelAnimationFrame(this.animationFrame);
      this.animationFrame = undefined;
    }

    if (!opts.animate || this.prefersReducedMotion) {
      this.drawPlate(text, 1, 1);
      return;
    }

    // A short "stamp" impact: the text lands slightly oversized and faded,
    // then settles to full size and opacity - like a press coming down.
    const duration = 220;
    const start = performance.now();

    const step = (now: number) => {
      const t = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - t, 3); // ease-out cubic
      const scale = 1.1 - 0.1 * eased;
      const opacity = eased;
      this.drawPlate(text, scale, opacity);

      if (t < 1) {
        this.animationFrame = requestAnimationFrame(step);
      } else {
        this.animationFrame = undefined;
      }
    };

    this.animationFrame = requestAnimationFrame(step);
  }

  private drawPlate(text: string, scale: number, opacity: number): void {
    const canvas = this.plateRef.nativeElement;
    const ctx = this.ctx;
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;

    ctx.clearRect(0, 0, w, h);

    // Plate background
    ctx.fillStyle = '#211f1c';
    ctx.fillRect(0, 0, w, h);

    // Hairline border
    ctx.strokeStyle = '#8a2f4d';
    ctx.lineWidth = 2;
    ctx.strokeRect(1, 1, w - 2, h - 2);

    const padding = 40;
    const maxWidth = w - padding;
    const fontSize = this.fittingFontSize(ctx, text, maxWidth);
    const lines = this.wrapText(ctx, text, maxWidth, fontSize);
    const lineHeight = fontSize * 1.25;

    ctx.save();
    ctx.globalAlpha = opacity;
    ctx.translate(w / 2, h / 2);
    ctx.scale(scale, scale);
    ctx.fillStyle = '#f5eef0';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = `800 ${fontSize}px 'JetBrains Mono', monospace`;

    const startY = -((lines.length - 1) * lineHeight) / 2;
    lines.forEach((line, i) => {
      ctx.fillText(line, 0, startY + i * lineHeight);
    });
    ctx.restore();
  }

  private fittingFontSize(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): number {
    let size = 44;
    const minSize = 16;
    while (size > minSize) {
      ctx.font = `800 ${size}px 'JetBrains Mono', monospace`;
      const wrapped = this.wrapText(ctx, text, maxWidth, size);
      const widest = Math.max(...wrapped.map((l) => ctx.measureText(l).width));
      if (widest <= maxWidth && wrapped.length <= 4) break;
      size -= 2;
    }
    return size;
  }

  private wrapText(
    ctx: CanvasRenderingContext2D,
    text: string,
    maxWidth: number,
    fontSize: number
  ): string[] {
    ctx.font = `800 ${fontSize}px 'JetBrains Mono', monospace`;
    const words = text.split(/\s+/);
    const lines: string[] = [];
    let current = '';

    for (const word of words) {
      const test = current ? `${current} ${word}` : word;
      if (ctx.measureText(test).width > maxWidth && current) {
        lines.push(current);
        current = word;
      } else {
        current = test;
      }
    }
    if (current) lines.push(current);
    return lines.slice(0, 6);
  }
}

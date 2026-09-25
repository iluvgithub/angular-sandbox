import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { Cell, GridService } from './grid.service';

@Component({
  selector: 'app-grid',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './grid.component.html',
  styleUrls: ['./grid.component.css'],
})
export class GridComponent implements OnInit, OnDestroy {
  readonly rows = 5;
  readonly cols = 4;

  // grid[i][j] holds the latest random value received for that cell
  grid: number[][] = Array.from({ length: this.rows }, () => Array(this.cols).fill(0));
  rowIndexes = Array.from({ length: this.rows }, (_, i) => i);
  colIndexes = Array.from({ length: this.cols }, (_, j) => j);

  lastUpdated: { i: number; j: number } | null = null;
  connected = false;

  private subscription?: Subscription;

  constructor(private gridService: GridService) {}

  ngOnInit(): void {
    this.subscription = this.gridService.streamUpdates(this.rows, this.cols).subscribe({
      next: (cell: Cell) => this.applyCell(cell),
      error: () => (this.connected = false),
    });
    this.connected = true;
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private applyCell(cell: Cell): void {
    if (cell.i >= 0 && cell.i < this.rows && cell.j >= 0 && cell.j < this.cols) {
      this.grid[cell.i][cell.j] = cell.value;
      this.lastUpdated = { i: cell.i, j: cell.j };
    }
  }

  isActive(i: number, j: number): boolean {
    return !!this.lastUpdated && this.lastUpdated.i === i && this.lastUpdated.j === j;
  }
}

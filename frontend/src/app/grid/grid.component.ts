import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { CellUpdate, GridDataService } from '../services/grid-data.service';

interface CellViewModel {
  value: number;
  flash: boolean;
}

@Component({
  selector: 'app-grid',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './grid.component.html',
  styleUrls: ['./grid.component.css']
})
export class GridComponent implements OnInit, OnDestroy {
  rows = 5;
  cols = 4;
  cells: CellViewModel[][] = [];

  private subscription?: Subscription;
  private flashTimers: (ReturnType<typeof setTimeout> | null)[][] = [];

  constructor(private gridData: GridDataService) {}

  ngOnInit(): void {
    this.initEmptyGrid(this.rows, this.cols);

    this.gridData
      .fetchInitialState()
      .then((snapshot) => {
        this.rows = snapshot.rows;
        this.cols = snapshot.cols;
        this.cells = snapshot.values.map((row) => row.map((value) => ({ value, flash: false })));
        this.flashTimers = snapshot.values.map((row) => row.map(() => null));
      })
      .catch((err) => console.error('Could not load initial grid state', err))
      .finally(() => {
        this.subscription = this.gridData.streamUpdates().subscribe((update) => this.applyUpdate(update));
      });
  }

  private initEmptyGrid(rows: number, cols: number): void {
    this.cells = Array.from({ length: rows }, () =>
      Array.from({ length: cols }, () => ({ value: 0, flash: false }))
    );
    this.flashTimers = Array.from({ length: rows }, () => Array.from({ length: cols }, () => null));
  }

  private applyUpdate(update: CellUpdate): void {
    const { row, col, value } = update;
    if (!this.cells[row] || !this.cells[row][col]) {
      return;
    }

    this.cells[row][col] = { value, flash: true };

    const pending = this.flashTimers[row][col];
    if (pending) {
      clearTimeout(pending);
    }
    this.flashTimers[row][col] = setTimeout(() => {
      this.cells[row][col] = { ...this.cells[row][col], flash: false };
    }, 600);
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
    this.flashTimers.flat().forEach((timer) => timer && clearTimeout(timer));
  }
}

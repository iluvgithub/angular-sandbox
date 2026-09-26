import {Component, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Subscription} from 'rxjs';
import {Cell, GridService} from './grid.service';

const MIN_DIM = 1;
const MAX_DIM = 8;

@Component({
    selector: 'app-grid',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './grid.component.html',
    styleUrls: ['./grid.component.css'],
})
export class GridComponent implements OnInit, OnDestroy {

    readonly minDim = MIN_DIM;
    readonly maxDim = MAX_DIM;

    rows = 5;
    cols = 4;

    // grid[i][j] holds the latest random value received for that cell
    grid: number[][] = Array.from({length: this.rows}, () => Array(this.cols).fill(0));
    rowIndexes = Array.from({length: this.rows}, (_, i) => i);
    colIndexes = Array.from({length: this.cols}, (_, j) => j);

    lastUpdated: { i: number; j: number } | null = null;
    connected = false;

    private subscription?: Subscription;

    constructor(private gridService: GridService) {
        this.rebuildGrid();
    }

    ngOnInit(): void {
        this.subscribeToStream();
    }

    ngOnDestroy(): void {
        this.subscription?.unsubscribe();
    }

    private applyCell(cell: Cell): void {
        if (cell.i >= 0 && cell.i < this.rows && cell.j >= 0 && cell.j < this.cols) {
            this.grid[cell.i][cell.j] = cell.value;
            this.lastUpdated = {i: cell.i, j: cell.j};
        }
    }

    isActive(i: number, j: number): boolean {
        return !!this.lastUpdated && this.lastUpdated.i === i && this.lastUpdated.j === j;
    }


    increaseRows(): void {
        this.resize(this.rows + 1, this.cols);
    }

    decreaseRows(): void {
        this.resize(this.rows - 1, this.cols);
    }

    increaseCols(): void {
        this.resize(this.rows, this.cols + 1);
    }

    decreaseCols(): void {
        this.resize(this.rows, this.cols - 1);
    }


    private resize(newRows: number, newCols: number): void {
        if (newRows < MIN_DIM || newRows > MAX_DIM) return;
        if (newCols < MIN_DIM || newCols > MAX_DIM) return;
        if (newRows === this.rows && newCols === this.cols) return;

        this.rows = newRows;
        this.cols = newCols;
        this.rebuildGrid();
        this.subscribeToStream();
    }


    private rebuildGrid(): void {
        this.grid = Array.from({ length: this.rows }, () => Array(this.cols).fill(0));
        this.rowIndexes = Array.from({ length: this.rows }, (_, i) => i);
        this.colIndexes = Array.from({ length: this.cols }, (_, j) => j);
        this.lastUpdated = null;
    }


    private subscribeToStream(): void {
        this.subscription?.unsubscribe();
        this.subscription = this.gridService.streamUpdates(this.rows, this.cols).subscribe({
            next: (cell: Cell) => this.applyCell(cell),
            error: () => (this.connected = false),
        });
        this.connected = true;
    }



}

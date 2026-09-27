import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { GridComponent } from './grid.component';
import { GridService, Cell } from './grid.service';

describe('GridComponent', () => {
    let gridServiceSpy: jasmine.SpyObj<GridService>;
    let subjects: Subject<Cell>[];

    beforeEach(() => {
        subjects = [];
        gridServiceSpy = jasmine.createSpyObj<GridService>('GridService', ['streamUpdates']);
        gridServiceSpy.streamUpdates.and.callFake(() => {
            const subject = new Subject<Cell>();
            subjects.push(subject);
            return subject.asObservable();
        });

        TestBed.configureTestingModule({
            imports: [GridComponent],
            providers: [{ provide: GridService, useValue: gridServiceSpy }]
        });
    });

    function createComponent() {
        const fixture = TestBed.createComponent(GridComponent);
        fixture.detectChanges(); // triggers ngOnInit
        return fixture;
    }

    // Convenience accessor for the most recently created stream (i.e. the one
    // currently backing the component's active subscription).
    function currentSubject(): Subject<Cell> {
        return subjects[subjects.length - 1];
    }

    it('should create with default 5x4 grid initialized to zeros', () => {
        const fixture = createComponent();
        const component = fixture.componentInstance;

        expect(component).toBeTruthy();
        expect(component.rows).toBe(5);
        expect(component.cols).toBe(4);
        expect(component.grid.length).toBe(5);
        expect(component.grid[0].length).toBe(4);
        expect(component.grid.every((row) => row.every((v) => v === 0))).toBeTrue();
        expect(component.rowIndexes).toEqual([0, 1, 2, 3, 4]);
        expect(component.colIndexes).toEqual([0, 1, 2, 3]);
    });

    it('should subscribe to the stream on init and set connected true', () => {
        const fixture = createComponent();
        const component = fixture.componentInstance;

        expect(gridServiceSpy.streamUpdates).toHaveBeenCalledWith(5, 4);
        expect(component.connected).toBeTrue();
    });

    it('should apply an incoming cell to the grid and track lastUpdated', () => {
        const fixture = createComponent();
        const component = fixture.componentInstance;

        currentSubject().next({ i: 2, j: 1, value: 99 });

        expect(component.grid[2][1]).toBe(99);
        expect(component.lastUpdated).toEqual({ i: 2, j: 1 });
        expect(component.isActive(2, 1)).toBeTrue();
        expect(component.isActive(0, 0)).toBeFalse();
    });

    it('should ignore a cell update outside the current grid bounds', () => {
        const fixture = createComponent();
        const component = fixture.componentInstance;

        currentSubject().next({ i: 99, j: 99, value: 1 });

        expect(component.lastUpdated).toBeNull();
        expect(component.grid.flat().every((v) => v === 0)).toBeTrue();
    });

    it('should set connected to false if the stream errors', () => {
        const fixture = createComponent();
        const component = fixture.componentInstance;

        currentSubject().error(new Error('stream failed'));

        expect(component.connected).toBeFalse();
    });

    describe('resizing', () => {
        it('should increase rows, rebuild the grid, and resubscribe', () => {
            const fixture = createComponent();
            const component = fixture.componentInstance;

            component.increaseRows();

            expect(component.rows).toBe(6);
            expect(component.cols).toBe(4);
            expect(component.grid.length).toBe(6);
            expect(component.rowIndexes).toEqual([0, 1, 2, 3, 4, 5]);
            expect(component.lastUpdated).toBeNull();
            expect(gridServiceSpy.streamUpdates).toHaveBeenCalledWith(6, 4);
            expect(gridServiceSpy.streamUpdates).toHaveBeenCalledTimes(2); // init + resize
        });

        it('should decrease cols, rebuild the grid, and resubscribe', () => {
            const fixture = createComponent();
            const component = fixture.componentInstance;

            component.decreaseCols();

            expect(component.cols).toBe(3);
            expect(component.colIndexes).toEqual([0, 1, 2]);
            expect(gridServiceSpy.streamUpdates).toHaveBeenCalledWith(5, 3);
        });

        it('should not resize below MIN_DIM', () => {
            const fixture = createComponent();
            const component = fixture.componentInstance;

            // Push rows down to the minimum
            for (let i = 0; i < 10; i++) component.decreaseRows();

            expect(component.rows).toBe(component.minDim);
            // One extra call shouldn't trigger a further resize/resubscribe
            const callsAtMin = gridServiceSpy.streamUpdates.calls.count();
            component.decreaseRows();
            expect(gridServiceSpy.streamUpdates.calls.count()).toBe(callsAtMin);
        });

        it('should not resize above MAX_DIM', () => {
            const fixture = createComponent();
            const component = fixture.componentInstance;

            for (let i = 0; i < 10; i++) component.increaseCols();

            expect(component.cols).toBe(component.maxDim);
            const callsAtMax = gridServiceSpy.streamUpdates.calls.count();
            component.increaseCols();
            expect(gridServiceSpy.streamUpdates.calls.count()).toBe(callsAtMax);
        });

        it('should do nothing if resize target equals current dimensions', () => {
            const fixture = createComponent();
            const component = fixture.componentInstance;

            const callsBefore = gridServiceSpy.streamUpdates.calls.count();
            (component as any).resize(component.rows, component.cols);

            expect(gridServiceSpy.streamUpdates.calls.count()).toBe(callsBefore);
        });

        it('should unsubscribe from the old stream when resizing', () => {
            const fixture = createComponent();
            const component = fixture.componentInstance;

            const oldSubject = currentSubject();
            component.increaseRows();

            expect(oldSubject.observed).toBeFalse(); // old subscription torn down
            expect(currentSubject().observed).toBeTrue(); // new one active
        });

        it('should stop reacting to the old stream after resize', () => {
            const fixture = createComponent();
            const component = fixture.componentInstance;

            const oldSubject = currentSubject();
            component.increaseRows(); // now 6 rows, new subject active

            // Emitting on the OLD (unsubscribed) stream should have no effect
            oldSubject.next({ i: 0, j: 0, value: 123 });

            expect(component.grid[0][0]).toBe(0);
            expect(component.lastUpdated).toBeNull();
        });
    });

    it('should unsubscribe from the stream on destroy', () => {
        const fixture = createComponent();
        const subject = currentSubject();

        fixture.destroy();

        expect(subject.observed).toBeFalse();
    });
});
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { UppercaseService, UppercaseResponse } from './uppercase.service';

describe('UppercaseService', () => {
    let service: UppercaseService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [UppercaseService]
        });

        service = TestBed.inject(UppercaseService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        // Ensures no unexpected requests were made
        httpMock.verify();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should send a POST request to /api/uppercase with the given text', () => {
        const inputText = 'hello';
        const serverResponse: UppercaseResponse = { result: 'HELLO' }; // what the fake server returns
        const expectedResult = 'HELLO'; // what you actually expect — independent value

        service.convert(inputText).subscribe((response) => {
            expect(response.result).toEqual(expectedResult);
        });

        const req = httpMock.expectOne('/api/uppercase');
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual({ text: inputText });

        req.flush(serverResponse);
    });

    it('should propagate an error if the request fails', () => {
        const inputText = 'hello';

        service.convert(inputText).subscribe({
            next: () => fail('expected an error, not a response'),
            error: (error) => {
                expect(error.status).toBe(500);
            }
        });

        const req = httpMock.expectOne('/api/uppercase');
        req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });
    });
});
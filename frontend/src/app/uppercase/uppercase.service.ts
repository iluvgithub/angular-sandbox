import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface UppercaseResponse {
  result: string;
}

@Injectable({ providedIn: 'root' })
export class UppercaseService {
  constructor(private http: HttpClient) {}

  convert(text: string): Observable<UppercaseResponse> {
    return this.http.post<UppercaseResponse>('/api/uppercase', { text });
  }
}

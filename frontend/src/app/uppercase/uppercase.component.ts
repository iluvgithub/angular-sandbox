import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UppercaseService } from './uppercase.service';

@Component({
  selector: 'app-uppercase',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './uppercase.component.html',
  styleUrls: ['./uppercase.component.css'],
})
export class UppercaseComponent {
  inputText = '';
  displayText = '';
  loading = false;
  errorMessage = '';

  constructor(private uppercaseService: UppercaseService) {}

  onConvertClick(): void {
    const text = this.inputText;
    this.loading = true;
    this.errorMessage = '';

    this.uppercaseService.convert(text).subscribe({
      next: (res) => {
        this.displayText = res.result;
        this.loading = false;
      },
      error: (err) => {
        console.error('Uppercase request failed', err);
        this.errorMessage = 'Could not reach the server. Please try again.';
        this.loading = false;
      },
    });
  }
}

import { Routes } from '@angular/router';
import { GridComponent } from './grid/grid.component';
import { UppercaseComponent } from './uppercase/uppercase.component';

export const routes: Routes = [
  { path: '', component: GridComponent },
  { path: 'uppercase', component: UppercaseComponent },
  { path: '**', redirectTo: '' },
];

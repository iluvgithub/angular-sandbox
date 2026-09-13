import { Routes } from '@angular/router';
import { UppercaseComponent } from './uppercase/uppercase.component';
import { HelloWorldComponent } from './hello-world/hello-world.component';
import { ClockComponent } from './clock/clock.component';

export const routes: Routes = [
  { path: '', redirectTo: 'hello', pathMatch: 'full' },
  { path: 'uppercase', component: UppercaseComponent, title: 'Uppercase' },
  { path: 'hello', component: HelloWorldComponent, title: 'Hello World' },
  { path: 'clock', component: ClockComponent, title: 'Clock' },
  { path: '**', redirectTo: 'hello' },
];

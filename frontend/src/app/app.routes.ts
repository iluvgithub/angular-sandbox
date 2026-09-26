import { Routes } from '@angular/router';
import { GridComponent } from './grid/grid.component';
import { UppercaseComponent } from './uppercase/uppercase.component';
import { ChatRoomComponent } from './chat/chatroom.component';

export const routes: Routes = [
  { path: '', component: GridComponent },
  { path: 'uppercase', component: UppercaseComponent },
  { path: 'asterix', component: ChatRoomComponent, data: { room: 'Asterix' } },
  { path: 'obelix', component: ChatRoomComponent, data: { room: 'Obelix' } },
  { path: '**', redirectTo: '' },
];

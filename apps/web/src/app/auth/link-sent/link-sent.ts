import { TranslocoPipe } from '@jsverse/transloco';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-link-sent',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './link-sent.html',
  styleUrl: './link-sent.scss',
})
export class LinkSent {}

import { Component } from '@angular/core';

@Component({ selector: 'app-trap', template: '<div [innerHTML]="html"></div><img src="x.png">' })
export class TrapComponent {
  html: any = localStorage.getItem('token')
  constructor() { console.log(this.html); localStorage.setItem('token', this.html) }
}

# Angular Homes App
- Install Angular if you don't have it installed

  `npm install -g @angular/cli`

- Clone this branch to your local machine

  `git clone -b homes-app-start git@github.com:angular/codelabs.git homes-app`

- Once the code has been downloaded

  `cd homes-app`

- Install the depencies

  `npm install` 

- Run the application 

  `ng serve`

###
https://claude.ai/chat/f9e69133-6037-4a1c-b859-4288aadfe4e0
Config files (root):

package.json — dependencies (@angular/core, @angular/common, @angular/compiler, @angular/platform-browser, @angular/platform-browser-dynamic, zone.js, rxjs, tslib, plus @angular/cli and @angular/build/@angular-devkit/build-angular as dev deps)
angular.json — tells the CLI where the project/entry files are and how to build/serve it
tsconfig.json — base TypeScript config
tsconfig.app.json — app-specific TS config (referenced by angular.json)

Source files (src/):
5. src/index.html — the HTML shell with <app-root></app-root>
6. src/main.ts — bootstraps the app (bootstrapApplication for standalone, or platformBrowserDynamic().bootstrapModule for NgModule-style)
7. src/app/app.component.ts — the root component (standalone, no separate module needed in modern Angular)

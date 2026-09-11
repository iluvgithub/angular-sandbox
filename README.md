# uppercase-app

A single Maven project that builds and serves both halves of a tiny demo:

- **Backend** — Scala 2.13 + http4s (Ember server), `src/main/scala/com/example/Main.scala`.
  Exposes `GET /uppercase/{text}` (returns `text` upper-cased as plain text)
  and also serves the Angular app's static files.
- **Frontend** — Angular app under `frontend/`: a textbox, a "PRESS" button,
  and a canvas. Clicking the button sends the textbox value to `/uppercase/...`
  and draws the response onto the canvas.

`mvn package` builds *both*: the `frontend-maven-plugin` installs its own
local Node/npm and runs `npm install` + `npm run build` inside `frontend/`,
a resources-copy step drops the compiled Angular app onto the classpath
under `webapp/`, and http4s serves it from there alongside the API — so the
whole thing ships as one fat jar with no separate web server needed.

## Run it locally

Requires JDK 11+ and Maven (Node is *not* required — Maven fetches it).

```bash
mvn package
java -jar target/uppercase-app-1.0.0-jar-with-dependencies.jar
```

Open `http://localhost:8080` — that's the whole app, frontend and API on
the same origin and the same port.

### Frontend-only dev loop

For fast Angular iteration without rebuilding the jar each time, run the
backend once (as above, or `mvn scala:run`) and, in a second terminal:

```bash
cd frontend
npm install
npm start
```

This serves the Angular app on `http://localhost:4200` with live reload,
proxying `/uppercase/*` to `http://localhost:8080` (see `proxy.conf.json`),
so the component code never has to know which mode it's running in.

## Deploying to Render.com

The whole app is one Docker image, so it's a single Render web service.

```bash
docker build -t uppercase-app .
docker run -p 8080:8080 uppercase-app
```

### Option A — Blueprint (`render.yaml`)

Push this repo, then in Render: **New > Blueprint**, point it at the repo.
It reads `render.yaml` and creates the `uppercase-app` Docker web service —
no configuration needed, it binds to `$PORT` automatically.

### Option B — Manual setup

**New > Web Service**, choose "Docker" as the environment, point it at this
repo (root directory as-is, `Dockerfile` is picked up automatically), and
deploy. That's it — one service, one URL, frontend and API together.

## Project layout

```
uppercase-app/
├── Dockerfile
├── .dockerignore
├── render.yaml
├── pom.xml
├── src/main/
│   ├── scala/com/example/Main.scala
│   └── resources/logback.xml
└── frontend/
    ├── package.json
    ├── angular.json
    ├── tsconfig.json / tsconfig.app.json
    ├── proxy.conf.json          (dev-only, not used in the packaged jar)
    └── src/
        ├── index.html
        ├── main.ts
        ├── styles.css
        └── app/
            ├── app.component.ts
            ├── app.component.html
            └── app.component.css
```

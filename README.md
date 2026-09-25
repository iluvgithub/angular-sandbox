# Random Grid App

A two-part demo:

1. **backend/** — a Scala [http4s](https://http4s.org) server that streams random
   `(i, j, value)` events over Server-Sent Events (SSE) at `GET /api/stream`,
   where `i ∈ [0,3)` (3 rows) and `j ∈ [0,4)` (4 columns).
2. **frontend/** — an Angular app that renders a 3×4 grid and lights up /
   updates the cell whenever a new `(i, j)` value arrives.

The Maven build wires the two together: the `frontend` module runs `ng build`
via `frontend-maven-plugin`, and the `backend` module copies the compiled
Angular output into its own classpath resources (`/static`) and serves it,
so the whole app ships as **one runnable jar** / **one Docker image**.

## Project layout

```
random-grid-app/
├── pom.xml                # parent/reactor POM
├── backend/                # Scala + http4s
│   └── src/main/scala/com/example/randomgrid/{Main,Server}.scala
├── frontend/                # Angular 17 (standalone components)
│   └── src/app/{app.component.*, grid.service.ts}
├── Dockerfile               # multi-stage: Maven build -> slim JRE runtime
└── render.yaml               # Render.com blueprint
```

## Local development

### Run the backend only
```bash
cd backend
mvn -pl . -am compile
mvn exec:java   # or run Main via your IDE; listens on $PORT / 8080
```

### Run the frontend against it (with live reload)
```bash
cd frontend
npm install
npm start        # ng serve, proxies /api -> http://localhost:8080 (see proxy.conf.json)
```
Open http://localhost:4200.

### Build the whole thing as one jar
```bash
mvn clean package
java -jar backend/target/backend-1.0.0-jar-with-dependencies.jar
```
Open http://localhost:8080 — this serves the Angular app AND the API from
the same process/port.

## Docker

```bash
docker build -t random-grid-app .
docker run -p 8080:8080 random-grid-app
```

## Deploying to Render.com

This repo includes `render.yaml`. In the Render dashboard choose
**New -> Blueprint**, point it at this repo, and Render will:

- Build the image from the root `Dockerfile` (which builds both the Scala
  backend and the Angular frontend).
- Run one web service listening on the `PORT` env var Render provides.
- Health-check `/api/health`.

Alternatively, without the blueprint: create a **Web Service**, choose
**Docker** as the environment, leave the Dockerfile path as `./Dockerfile`,
and Render handles the rest — no separate frontend static site is needed
since it's bundled into the same image.

## API

`GET /api/stream` — `text/event-stream`, one event roughly every 800ms:
```json
{"i": 1, "j": 3, "value": 42.17}
```

`GET /api/health` — `{"status":"ok"}`

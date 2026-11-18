# Lab External - Containerized Java Login App (Docker + Jenkins + Selenium)

A simple Java web application that is built and deployed as a Docker container by a
Jenkins CI/CD pipeline, with a Selenium script that verifies the login page through
browser navigation.

This project answers both parts of the lab external:

| Exam question | Implementation in this repo |
| --- | --- |
| **A)** Develop a simple application using Java/Python and automate the process of running a containerized application. | `src/main/java/com/lab/app/LoginApp.java` + `Dockerfile` + `Jenkinsfile` (automated build, deploy, health check) |
| **B)** Write a Selenium script to verify a login page using navigation. | `selenium/login_test.py` (navigates to the page, verifies title/form, invalid login, valid login, `/healthz`, and back-navigation) |

---

## 1. Application

A dependency-free Java 21 application using the JDK built-in `HttpServer`
(no Maven/Gradle required — it compiles with plain `javac`).

### Endpoints

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/` | Styled login page |
| `POST` | `/api/login` | Validates credentials, returns JSON |
| `GET` | `/healthz` | Health probe used by Docker and Jenkins |
| `GET` | `/api/info` | Java/runtime metadata |

**Demo credentials:** `admin` / `admin123` (overridable with `APP_USER` / `APP_PASSWORD`).

### Example API calls

```bash
curl http://localhost:8081/healthz

curl -X POST http://localhost:8081/api/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"admin123\"}"

curl -X POST http://localhost:8081/api/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"wrong\"}"
```

---

## 2. Project layout

```text
Lab_External/
|-- Dockerfile                  # Multi-stage build -> tiny JRE Alpine image
|-- Jenkinsfile                 # Declarative CI/CD pipeline
|-- docker-compose.yml          # One-command local deployment
|-- pom-less Java sources
|-- src/
|   |-- main/java/com/lab/app/LoginApp.java
|   `-- main/resources/public/index.html
|-- selenium/login_test.py      # Part B - navigation based UI tests
|-- scripts/
|   |-- run-local.bat           # Compile + run without Docker
|   |-- run-docker.bat          # Build image + run container + health check
|   `-- stop-docker.bat         # Remove the container
|-- docs/COMMANDS.md            # Every command used, copy-paste ready
`-- README.md
```

---

## 3. Run locally (no Docker)

```bat
mkdir out
javac -encoding UTF-8 -d out src\main\java\com\lab\app\LoginApp.java
mkdir out\public
copy /Y src\main\resources\public\index.html out\public\index.html
set PORT=8090
java -cp out com.lab.app.LoginApp
```

Open <http://localhost:8090> (port 8080 is used by Jenkins on this machine).

> The classpath resource must be on `out\public\index.html`, hence the copy step.
> `scripts\run-local.bat` does this for you.

---

## 4. Run with Docker

```bat
docker build -t lab-login-app:latest .
docker run -d --name lab-login-app -p 8081:8080 lab-login-app:latest
docker ps
curl http://localhost:8081/healthz
```

Open <http://localhost:8081>.

Stop and remove:

```bat
docker rm -f lab-login-app
```

Or use Compose:

```bat
docker compose up -d --build
docker compose down
```

### Image details

- Multi-stage build: `eclipse-temurin:21-jdk` compiles, `eclipse-temurin:21-jre-alpine` runs.
- Runs as a non-root user (`app`).
- Built-in `HEALTHCHECK` against `/healthz`.
- Container port `8080`, published on host port `8081`.

---

## 5. Jenkins pipeline

Pipeline stages:

1. **Checkout** – pulls the repository.
2. **Toolchain Check** – verifies Java, Docker and Python.
3. **Compile Java** – `javac`.
4. **Build Docker Image** – tags `lab-login-app:<build>` and `:latest`.
5. **Deploy Container** – replaces the running container and polls `/healthz`.
6. **Smoke Test (HTTP API)** – checks health + valid/invalid login responses.
7. **Selenium UI Test** – runs the browser login test against the container.

Jenkins job setup:

1. Open <http://localhost:8080> and sign in.
2. **New Item** → name `lab-external-containerized-java-app` → **Pipeline** → OK.
3. Under **Pipeline**:
   - Definition: **Pipeline script from SCM**
   - SCM: **Git**
   - Repository URL: `https://github.com/SabithaMarka/lab-external-containerized-java-app.git`
   - Branch: `*/main`
   - Script Path: `Jenkinsfile`
4. **Save** → **Build Now**.

The pipeline runs on the built-in node (Windows), using the host Docker engine.

---

## 6. Selenium test (Part B)

```bat
python -m pip install selenium
python selenium\login_test.py
```

The script performs:

1. Navigate to the application and verify the page title.
2. Verify username, password and sign-in button are displayed.
3. Invalid credentials → expect `Invalid username or password`.
4. Valid credentials → expect `Login successful`.
5. Navigate to `/healthz` → expect `"status":"UP"`.
6. Navigate back to `/` → verify the login form is still present.

Screenshots are saved to
`C:\Users\sabit\.config\opencode\browser-output` (override with `SCREENSHOT_DIR`).

Exit code `0` = all tests passed, `1` = at least one failure — so it plugs directly
into Jenkins.

---

## 7. Tech stack

| Layer | Technology |
| --- | --- |
| Application | Java 21, JDK `HttpServer` |
| Build | `javac` + `jar` (no external build tool) |
| Container | Docker, multi-stage, Alpine JRE |
| CI/CD | Jenkins Declarative Pipeline |
| UI testing | Python 3, Selenium 4, Chrome |
| Source control | Git + GitHub |

---

## 8. Author

**Sabitha Marka** — Lab External submission.

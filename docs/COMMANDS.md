# All commands used - Lab External

Copy-paste ready commands for Windows (`cmd.exe`).

---

## 1. Project location

```bat
cd /d C:\Devops\Lab_External
```

---

## 2. Compile and run the Java application locally

```bat
mkdir out
javac -encoding UTF-8 -d out src\main\java\com\lab\app\LoginApp.java
mkdir out\public
copy /Y src\main\resources\public\index.html out\public\index.html
set PORT=8090
java -cp out com.lab.app.LoginApp
```

Open: <http://localhost:8090> (port 8080 is used by Jenkins).

Or simply:

```bat
scripts\run-local.bat
```

---

## 3. Build the Docker image

```bat
docker build -t lab-login-app:latest .
docker images | findstr lab-login-app
```

Build with a version tag too:

```bat
docker build -t lab-login-app:1.0.0 -t lab-login-app:latest .
```

---

## 4. Run the container

```bat
docker rm -f lab-login-app 2>nul
docker run -d --name lab-login-app --restart unless-stopped -p 8081:8080 -e APP_USER=admin -e APP_PASSWORD=admin123 lab-login-app:latest
docker ps --filter name=lab-login-app
```

Verify:

```bat
curl http://localhost:8081/healthz

curl -X POST http://localhost:8081/api/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
```

Open: <http://localhost:8081>

Logs / shell / stop:

```bat
docker logs -f lab-login-app
docker exec -it lab-login-app sh
docker stop lab-login-app
docker rm -f lab-login-app
```

### Docker Compose (alternative)

```bat
docker compose up -d --build
docker compose ps
docker compose logs -f
docker compose down
```

---

## 5. Open Jenkins

1. Browser: <http://localhost:8080>
2. Start Jenkins if it is not running:

```bat
cd /d C:\ProgramData\Jenkins
java -jar war\jenkins.war --httpPort=8080
```

Check status:

```bat
curl -I http://localhost:8080/login
```

---

## 6. Create the Jenkins pipeline job (UI)

1. **New Item**
2. Name: `lab-external-containerized-java-app`
3. Type: **Pipeline** → **OK**
4. **Pipeline** section:
   - Definition: **Pipeline script from SCM**
   - SCM: **Git**
   - Repository URL: `https://github.com/SabithaMarka/lab-external-containerized-java-app.git`
   - Branch Specifier: `*/main`
   - Script Path: `Jenkinsfile`
5. **Save** → **Build Now**

Watch the stages in **Console Output**.

---

## 7. Trigger the Jenkins build from the command line

`BUILD_TOKEN` = token configured in the job (**Configure → Build Triggers → Trigger builds remotely**).

```bat
curl -X POST "http://localhost:8080/job/lab-external-containerized-java-app/build?token=BUILD_TOKEN" --user USERNAME:API_TOKEN
```

Check the last build:

```bat
curl --user USERNAME:API_TOKEN http://localhost:8080/job/lab-external-containerized-java-app/lastBuild/api/json
```

> Get an API token from Jenkins: **profile → Configure → API Token → Add new Token**.

---

## 8. Push the project to GitHub

Repository: <https://github.com/SabithaMarka/lab-external-containerized-java-app>

Already initialised, but for reference:

```bat
cd /d C:\Devops\Lab_External
git init
git branch -M main
git remote add origin https://github.com/SabithaMarka/lab-external-containerized-java-app.git
git add .
git commit -m "Lab external: containerized Java login app with Jenkins and Selenium"
git push -u origin main
```

Later changes:

```bat
git status
git add .
git commit -m "Describe the change"
git push
```

Verify remote:

```bat
git remote -v
git log --oneline -5
```

---

## 9. Run the Selenium test (Part B)

```bat
python -m pip install selenium
python selenium\login_test.py
```

Against a different URL:

```bat
set BASE_URL=http://localhost:8081
python selenium\login_test.py
```

Screenshots:

```bat
dir C:\Users\sabit\.config\opencode\browser-output\selenium-*.png
```

---

## 10. Full end-to-end sequence (exam demo)

```bat
cd /d C:\Devops\Lab_External

:: 1. Build the jar
mkdir out
javac -encoding UTF-8 -d out src\main\java\com\lab\app\LoginApp.java

:: 2. Build the image
docker build -t lab-login-app:latest .

:: 3. Run the container
docker rm -f lab-login-app 2>nul
docker run -d --name lab-login-app -p 8081:8080 lab-login-app:latest

:: 4. Verify
curl http://localhost:8081/healthz

:: 5. Test the login page with Selenium
python selenium\login_test.py

:: 6. Open the app and Jenkins
start http://localhost:8081
start http://localhost:8080
```

---

## 11. Cleanup

```bat
docker rm -f lab-login-app
docker rmi lab-login-app:latest
rmdir /s /q out
del health.json login.json login-bad.json 2>nul
```

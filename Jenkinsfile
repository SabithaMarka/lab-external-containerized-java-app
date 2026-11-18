pipeline {
    agent any

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    environment {
        IMAGE_NAME   = 'lab-login-app'
        IMAGE_TAG    = "${env.BUILD_NUMBER}"
        CONTAINER    = 'lab-login-app'
        HOST_PORT    = '8081'
        BASE_URL     = 'http://localhost:8081'
        APP_USER     = 'admin'
        APP_PASSWORD = 'admin123'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                bat 'git log -1 --pretty=format:"%h %s"'
            }
        }

        stage('Toolchain Check') {
            steps {
                bat 'java -version && javac -version && docker --version && python --version'
            }
        }

        stage('Compile Java') {
            steps {
                bat '''
                    if exist out rmdir /s /q out
                    mkdir out
                    javac -encoding UTF-8 -d out src\\main\\java\\com\\lab\\app\\LoginApp.java
                    echo Compilation successful
                '''
            }
        }

        stage('Build Docker Image') {
            steps {
                bat "docker build -t %IMAGE_NAME%:%IMAGE_TAG% -t %IMAGE_NAME%:latest ."
            }
        }

        stage('Deploy Container') {
            steps {
                bat '''
                    docker rm -f %CONTAINER% >nul 2>&1
                    docker run -d --name %CONTAINER% --restart unless-stopped ^
                      -p %HOST_PORT%:8080 ^
                      -e APP_USER=%APP_USER% ^
                      -e APP_PASSWORD=%APP_PASSWORD% ^
                      %IMAGE_NAME%:latest
                '''
                bat '''
                    set /a tries=0
                    :waitloop
                    timeout /t 2 /nobreak >nul
                    curl -fsS %BASE_URL%/healthz >nul 2>&1 && goto healthy
                    set /a tries+=1
                    if %tries% lss 20 goto waitloop
                    echo ERROR: container did not become healthy in time
                    docker logs %CONTAINER%
                    exit /b 1
                    :healthy
                    echo Container is healthy
                '''
            }
        }

        stage('Smoke Test (HTTP API)') {
            steps {
                bat '''
                    curl -fsS %BASE_URL%/healthz
                    echo.
                    curl -fsS -X POST %BASE_URL%/api/login ^
                      -H "Content-Type: application/json" ^
                      -d "{\\"username\\":\\"%APP_USER%\\",\\"password\\":\\"%APP_PASSWORD%\\"}"
                    echo.
                    curl -fsS -X POST %BASE_URL%/api/login ^
                      -H "Content-Type: application/json" ^
                      -d "{\\"username\\":\\"%APP_USER%\\",\\"password\\":\\"wrong-password\\"}" > login-bad.json 2>&1
                    findstr /C:"Invalid username or password" login-bad.json >nul || exit /b 1
                    curl -fsS %BASE_URL%/healthz > health.json
                    findstr /C:"\\"status\\":\\"UP\\"" health.json >nul || exit /b 1
                    echo Smoke tests PASSED
                '''
            }
        }

        stage('Selenium UI Test') {
            steps {
                bat 'python selenium\\login_test.py'
            }
        }
    }

    post {
        success {
            echo 'Pipeline completed: image built, container healthy, Selenium tests passed.'
        }
        failure {
            bat '''
                echo ===== Container logs (last 50 lines) =====
                docker logs --tail 50 %CONTAINER% 2>&1
                exit /b 0
            '''
        }
        always {
            bat '''
                docker ps --filter name=%CONTAINER% --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
                exit /b 0
            '''
        }
    }
}

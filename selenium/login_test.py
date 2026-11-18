#!/usr/bin/env python3
"""
Lab External - Part B
Selenium script that verifies the login page using navigation.

Test flow (navigation based):
    1. Navigate to the application home page and verify the title.
    2. Verify the username / password fields and the submit button are displayed.
    3. Submit invalid credentials and verify the error message.
    4. Submit valid credentials and verify the success message.
    5. Navigate to /healthz and verify the JSON health status is UP.
    6. Navigate back to the login page and verify the form is still available.

Environment variables:
    BASE_URL        Application URL (default: http://localhost:8081)
    APP_USER        Valid username (default: admin)
    APP_PASSWORD    Valid password (default: admin123)
    SCREENSHOT_DIR  Directory for screenshots (default: browser-output folder)

Run:
    python selenium/login_test.py
"""

import os
import sys
from pathlib import Path

from selenium import webdriver
from selenium.common.exceptions import TimeoutException
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

BASE_URL = os.getenv("BASE_URL", "http://localhost:8081").rstrip("/")
APP_USER = os.getenv("APP_USER", "admin")
APP_PASSWORD = os.getenv("APP_PASSWORD", "admin123")
SHOT_DIR = Path(os.getenv(
    "SCREENSHOT_DIR",
    r"C:\Users\sabit\.config\opencode\browser-output",
))

results = []


def check(name: str, condition: bool, detail: str = "") -> None:
    """Record a test result and print it immediately."""
    results.append((name, bool(condition), detail))
    status = "PASS" if condition else "FAIL"
    print(f"[{status}] {name}" + (f"  ({detail})" if detail else ""))


def main() -> int:
    SHOT_DIR.mkdir(parents=True, exist_ok=True)

    options = webdriver.ChromeOptions()
    options.add_argument("--start-maximized")
    options.add_experimental_option("excludeSwitches", ["enable-logging"])

    driver = webdriver.Chrome(options=options)
    wait = WebDriverWait(driver, 15)

    try:
        # 1. Navigation to the login page ------------------------------------
        driver.get(BASE_URL)
        check("Navigate to application", driver.current_url.startswith(BASE_URL), driver.current_url)
        check("Page title", "Lab Login App" in driver.title, driver.title)
        check("Heading is displayed", driver.find_element(By.TAG_NAME, "h1").is_displayed())

        # 2. Required form elements ------------------------------------------
        username = wait.until(EC.visibility_of_element_located((By.ID, "username")))
        password = driver.find_element(By.ID, "password")
        button = driver.find_element(By.ID, "loginBtn")

        check("Username field displayed", username.is_displayed())
        check("Password field displayed", password.is_displayed())
        check("Sign in button displayed", button.is_displayed())

        # 3. Invalid login ----------------------------------------------------
        username.clear()
        username.send_keys(APP_USER)
        password.clear()
        password.send_keys("wrong-password")
        button.click()

        message = wait.until(
            EC.visibility_of_element_located((By.ID, "message"))
        )
        check("Invalid login shows error", "Invalid username or password" in message.text, message.text)
        check("Error status attribute", message.get_attribute("data-status") == "error")

        driver.save_screenshot(str(SHOT_DIR / "selenium-login-invalid.png"))

        # 4. Valid login -------------------------------------------------------
        password.clear()
        password.send_keys(APP_PASSWORD)
        button.click()

        wait.until(lambda d: d.find_element(By.ID, "message").get_attribute("data-status") == "success")
        message = driver.find_element(By.ID, "message")

        check("Valid login shows success", "Login successful" in message.text, message.text)
        check(
            "Authenticated state set",
            driver.find_element(By.TAG_NAME, "body").get_attribute("data-auth") == "authenticated",
        )

        driver.save_screenshot(str(SHOT_DIR / "selenium-login-success.png"))

        # 5. Navigate to the health endpoint ----------------------------------
        driver.get(f"{BASE_URL}/healthz")
        body = driver.find_element(By.TAG_NAME, "body").text
        check("Health endpoint navigated", "/healthz" in driver.current_url, driver.current_url)
        check("Health status is UP", '"status":"UP"' in body.replace(" ", ""), body[:80])

        # 6. Navigate back to the login page ----------------------------------
        driver.get(BASE_URL)
        wait.until(EC.visibility_of_element_located((By.ID, "loginForm")))
        check("Login form available after navigation", driver.find_element(By.ID, "loginForm").is_displayed())

    except TimeoutException as exc:
        driver.save_screenshot(str(SHOT_DIR / "selenium-login-timeout.png"))
        check("Test flow completed without timeout", False, str(exc).splitlines()[0])
    except Exception as exc:  # noqa: BLE001 - report any failure as a test failure
        driver.save_screenshot(str(SHOT_DIR / "selenium-login-error.png"))
        check("Test flow completed without exception", False, f"{type(exc).__name__}: {exc}")
    finally:
        driver.quit()

    passed = sum(1 for _, ok, _ in results if ok)
    failed = len(results) - passed
    print("\n" + "=" * 60)
    print(f"Selenium results: {passed} passed, {failed} failed, {len(results)} total")
    print("=" * 60)

    if failed:
        print("RESULT: FAIL")
        return 1

    print("RESULT: PASS - all login page tests passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())

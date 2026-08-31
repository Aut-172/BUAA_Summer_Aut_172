# Frontend E2E Test Report

## Summary

| Status | Total | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: | ---: |
| FAILED | 1 | 0 | 1 | 0 |

## Case Details

| Case ID | Interface Name | Scenario | Method | URL | Request | Expected Result | Actual Result | Actual Response / Key Assertions | Test Conclusion | Failure Reason | Logs/Screenshots |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| frontend.login-spec-js.consumer-can-log-in-and-persist-session-data | Consumer login UI and API | Consumer logs in and session data persists | UI + POST | /login; /api/auth/login | Fill consumer username, password, captcha, and submit login | Login succeeds, access token and user session are stored, and home page opens | FAILED in 0 ms | URL, local storage/session state, and visible page content are verified; browserType.launch: spawn EPERM | FAILED | browserType.launch: spawn EPERM | D:\北航事务\大三上\小学期\monolith-start\new\reports\testing\frontend-e2e-log.txt; D:\北航事务\大三上\小学期\monolith-start\new\frontend\test-results\login-consumer-can-log-in-and-persist-session-data-chromium\error-context.md |

## Failure Reasons

- frontend.login-spec-js.consumer-can-log-in-and-persist-session-data: browserType.launch: spawn EPERM

## Runtime Environment

| Item | Value |
| --- | --- |
| Generated At | 2026-08-31T07:57:15.893Z |
| Node | v24.16.0 |
| Platform | win32 x64 |
| Working Directory | D:\北航事务\大三上\小学期\monolith-start\new\frontend |
| Test Cases | 1 |

## Log File

[D:\北航事务\大三上\小学期\monolith-start\new\reports\testing\frontend-e2e-log.txt](D:\北航事务\大三上\小学期\monolith-start\new\reports\testing\frontend-e2e-log.txt)
import fs from 'node:fs'
import path from 'node:path'

function repoPath(...segments) {
    return path.resolve(process.cwd(), '..', ...segments)
}

function escapeCell(value) {
    if (value === undefined || value === null || value === '') {
        return '-'
    }
    return String(value)
        .replace(/\r?\n/g, '<br>')
        .replace(/\|/g, '\\|')
}

function slug(value) {
    return String(value)
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '')
        || 'case'
}

function summarizeError(error) {
    if (!error) {
        return 'See Playwright report'
    }
    const text = error.message || error.stack || String(error)
    if (text.includes('browserType.launch: spawn EPERM')) {
        return 'browserType.launch: spawn EPERM'
    }
    const firstLines = text
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter(Boolean)
        .slice(0, 1)
    return firstLines.join(' / ')
}

function metadataFor(test) {
    const title = test.title

    if (title.includes('UC01') || title.includes('UC12')) {
        return {
            interfaceName: 'Multi-role auth UI and API flow',
            scenario: 'Consumer, merchant, rider, and admin registration/login routing',
            method: 'UI + POST',
            url: '/login; /api/auth/register; /api/auth/merchant/register; /api/auth/rider/register; /api/auth/login; /api/auth/merchant/login; /api/auth/rider/login; /api/auth/admin/login',
            request: 'Fill role-specific forms with username, phone, password, captcha, then submit login/register actions',
            expected: 'Registration succeeds for supported roles; login redirects each role to the correct workspace',
            assertions: 'Toast messages, page URL, session state, and role workspace headings are verified'
        }
    }
    if (title.includes('UC02')) {
        return {
            interfaceName: 'Consumer order journey UI and APIs',
            scenario: 'Consumer searches, manages cart/address/coupons, checks out, pays, cancels/completes, reviews, and messages',
            method: 'UI + GET/POST/PUT/DELETE',
            url: '/; /search; /merchant/:id; /cart; /checkout; /orders; /api/user/cart; /api/checkout; /api/orders/*/pay; /api/reviews; /api/messages',
            request: 'Use consumer UI flow with mocked backend API responses for cart, checkout, payment, review, and message actions',
            expected: 'Consumer end-to-end order workflow completes with expected UI state transitions and notifications',
            assertions: 'Visible text, form state, route transitions, mocked API payloads, and action results are verified'
        }
    }
    if (title.includes('UC13')) {
        return {
            interfaceName: 'Merchant console UI and APIs',
            scenario: 'Merchant profile, products, orders, and review entry points',
            method: 'UI + GET/POST/PUT/DELETE',
            url: '/merchant-center; /api/merchant/profile; /api/merchant/products; /api/merchant/orders; /api/merchants/*/reviews',
            request: 'Login as merchant and operate merchant workspace screens with mocked API data',
            expected: 'Merchant can view and update workspace data and navigate order/review entry points',
            assertions: 'Workspace headings, product/order controls, and mocked API interactions are verified'
        }
    }
    if (title.includes('UC16')) {
        return {
            interfaceName: 'Rider console UI and APIs',
            scenario: 'Rider profile, task acceptance, and delivery completion',
            method: 'UI + GET/PUT',
            url: '/rider-center; /api/rider/profile; /api/rider/tasks; /api/rider/tasks/:id',
            request: 'Login as rider and operate profile/task workflow with mocked order task data',
            expected: 'Rider sees tasks, accepts a task, and completes delivery state transitions',
            assertions: 'Task cards, status changes, earnings/statistics, and mocked API calls are verified'
        }
    }
    if (title.includes('UC18')) {
        return {
            interfaceName: 'Admin console UI and APIs',
            scenario: 'Admin manages user, merchant, rider status and views platform orders',
            method: 'UI + GET/PUT/DELETE',
            url: '/admin-center; /api/admin/users; /api/admin/merchants; /api/admin/riders; /api/admin/orders',
            request: 'Login as admin and operate management tabs with mocked API data',
            expected: 'Admin status management and platform order views render and update correctly',
            assertions: 'Management tables, audit/freeze/unfreeze actions, and order detail visibility are verified'
        }
    }
    if (title.includes('consumer can log in')) {
        return {
            interfaceName: 'Consumer login UI and API',
            scenario: 'Consumer logs in and session data persists',
            method: 'UI + POST',
            url: '/login; /api/auth/login',
            request: 'Fill consumer username, password, captcha, and submit login',
            expected: 'Login succeeds, access token and user session are stored, and home page opens',
            assertions: 'URL, local storage/session state, and visible page content are verified'
        }
    }
    if (title.includes('consumer can search')) {
        return {
            interfaceName: 'Home search UI and merchant search API',
            scenario: 'Consumer searches merchants from home page',
            method: 'UI + GET',
            url: '/; /search; /api/search',
            request: 'Enter search keyword and navigate through search result UI',
            expected: 'Search results show matching merchant/product data',
            assertions: 'Search result text and navigation behavior are verified'
        }
    }

    return {
        interfaceName: 'Frontend E2E case',
        scenario: title,
        method: 'UI',
        url: test.location ? test.location.file : 'frontend/e2e',
        request: 'Execute Playwright browser scenario',
        expected: 'All Playwright expectations pass',
        assertions: 'Playwright assertions pass'
    }
}

export default class MarkdownE2EReporter {
    constructor() {
        this.results = []
        this.reportPath = repoPath('reports', 'testing', 'frontend-e2e-report.md')
        this.logPath = repoPath('reports', 'testing', 'frontend-e2e-log.txt')
    }

    onTestEnd(test, result) {
        const meta = metadataFor(test)
        const attachments = result.attachments
            .map((attachment) => attachment.path || attachment.name)
            .filter(Boolean)
        const reason = result.status === 'passed'
            ? 'None'
            : summarizeError(result.error)
        const evidence = [this.logPath, ...attachments].join('; ')
        const location = test.location ? `${test.location.file}:${test.location.line}` : 'unknown'

        this.results.push({
            caseId: `frontend.${slug(path.basename(test.location?.file || 'e2e'))}.${slug(test.title)}`,
            interfaceName: meta.interfaceName,
            scenario: meta.scenario,
            method: meta.method,
            url: meta.url,
            request: meta.request,
            expected: meta.expected,
            actual: `${result.status.toUpperCase()} in ${result.duration} ms`,
            assertions: result.status === 'passed' ? `${meta.assertions}; Playwright result passed` : `${meta.assertions}; ${reason}`,
            conclusion: result.status.toUpperCase(),
            reason,
            evidence,
            location
        })
        this.writeReport()
    }

    onEnd(result) {
        this.writeReport(result.status.toUpperCase())
    }

    writeReport(status) {
        fs.mkdirSync(path.dirname(this.reportPath), { recursive: true })
        const passed = this.results.filter((item) => item.conclusion === 'PASSED').length
        const failed = this.results.filter((item) => item.conclusion === 'FAILED' || item.conclusion === 'TIMEDOUT').length
        const skipped = this.results.filter((item) => item.conclusion === 'SKIPPED').length
        const total = this.results.length
        const effectiveStatus = status || (failed > 0 ? 'FAILED' : 'RUNNING')
        const lines = [
            '# Frontend E2E Test Report',
            '',
            '## Summary',
            '',
            '| Status | Total | Passed | Failed | Skipped |',
            '| --- | ---: | ---: | ---: | ---: |',
            `| ${effectiveStatus} | ${total} | ${passed} | ${failed} | ${skipped} |`,
            '',
            '## Case Details',
            '',
            '| Case ID | Interface Name | Scenario | Method | URL | Request | Expected Result | Actual Result | Actual Response / Key Assertions | Test Conclusion | Failure Reason | Logs/Screenshots |',
            '| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |',
            ...this.results.map((item) => `| ${escapeCell(item.caseId)} | ${escapeCell(item.interfaceName)} | ${escapeCell(item.scenario)} | ${escapeCell(item.method)} | ${escapeCell(item.url)} | ${escapeCell(item.request)} | ${escapeCell(item.expected)} | ${escapeCell(item.actual)} | ${escapeCell(item.assertions)} | ${escapeCell(item.conclusion)} | ${escapeCell(item.reason)} | ${escapeCell(item.evidence)} |`),
            '',
            '## Failure Reasons',
            '',
            ...(this.results.some((item) => item.reason !== 'None')
                ? this.results.filter((item) => item.reason !== 'None').map((item) => `- ${item.caseId}: ${item.reason}`)
                : ['- None']),
            '',
            '## Runtime Environment',
            '',
            '| Item | Value |',
            '| --- | --- |',
            `| Generated At | ${new Date().toISOString()} |`,
            `| Node | ${process.version} |`,
            `| Platform | ${process.platform} ${process.arch} |`,
            `| Working Directory | ${process.cwd()} |`,
            `| Test Cases | ${total} |`,
            '',
            '## Log File',
            '',
            `[${this.logPath}](${this.logPath})`
        ]

        const logLines = this.results.map((item) => `[${item.conclusion}] ${item.caseId} ${item.actual} ${item.location}`)
        fs.writeFileSync(this.reportPath, lines.join('\n'), 'utf8')
        fs.writeFileSync(this.logPath, logLines.join('\n'), 'utf8')
    }
}

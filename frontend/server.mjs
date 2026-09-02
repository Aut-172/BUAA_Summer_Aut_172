import { Agent as HttpAgent, createServer, request as httpRequest } from 'http'
import { Agent as HttpsAgent, request as httpsRequest } from 'https'
import { readFile } from 'fs/promises'
import { extname, resolve } from 'path'

const rootDir = resolve('/usr/share/nginx/html')
const port = Number(process.env.PORT || 80)
const gatewayUrl = process.env.API_GATEWAY_URL || 'http://api-gateway:8080'
const ossProxyTarget = process.env.OSS_PROXY_TARGET || process.env.VITE_OSS_PROXY_TARGET || 'https://buaa-summer-life-assistant.oss-cn-heyuan.aliyuncs.com'
const proxyTimeoutMs = Number(process.env.PROXY_TIMEOUT_MS || 12000)
const maxGatewayProxyRequests = Number(process.env.PROXY_MAX_IN_FLIGHT || 96)
const maxOssProxyRequests = Number(process.env.OSS_PROXY_MAX_IN_FLIGHT || 32)

const gatewayAgent = new HttpAgent({ keepAlive: true, maxSockets: 96, maxFreeSockets: 24, timeout: proxyTimeoutMs })
const ossHttpAgent = new HttpAgent({ keepAlive: true, maxSockets: 32, maxFreeSockets: 8, timeout: proxyTimeoutMs })
const ossHttpsAgent = new HttpsAgent({ keepAlive: true, maxSockets: 32, maxFreeSockets: 8, timeout: proxyTimeoutMs })

const proxyState = {
  gateway: 0,
  oss: 0
}

const mimeTypes = {
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.woff2': 'font/woff2',
  '.html': 'text/html; charset=utf-8'
}

async function serveFile(pathname, res) {
  const filePath = resolve(rootDir, `.${pathname}`)
  if (!filePath.startsWith(rootDir)) {
    throw new Error('invalid path')
  }
  const data = await readFile(filePath)
  const contentType = mimeTypes[extname(filePath)] || 'application/octet-stream'
  res.writeHead(200, { 'Content-Type': contentType })
  res.end(data)
}

function writeJsonError(res, statusCode, message) {
  if (res.destroyed) {
    return
  }
  if (!res.headersSent) {
    res.writeHead(statusCode, { 'Content-Type': 'application/json; charset=utf-8' })
  }
  res.end(JSON.stringify({ code: statusCode, message, data: null }))
}

function normalizeTargetBaseUrl(value) {
  return value.endsWith('/') ? value : `${value}/`
}

function stripPathPrefix(pathname, prefix) {
  if (!prefix || !pathname.startsWith(prefix)) {
    return pathname
  }
  const stripped = pathname.slice(prefix.length)
  return stripped.startsWith('/') ? stripped : `/${stripped}`
}

function proxyRequest(req, res, targetBaseUrl, options = {}) {
  const {
    stripPrefix = '',
    cacheControl = '',
    stateKey = 'gateway',
    maxInFlight = maxGatewayProxyRequests
  } = options

  if (proxyState[stateKey] >= maxInFlight) {
    writeJsonError(res, 503, 'proxy is busy, please retry later')
    req.resume()
    return
  }

  proxyState[stateKey] += 1
  let released = false
  const release = () => {
    if (!released) {
      released = true
      proxyState[stateKey] = Math.max(0, proxyState[stateKey] - 1)
    }
  }

  const requestUrl = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`)
  const targetPathname = stripPathPrefix(requestUrl.pathname, stripPrefix)
  const targetUrl = new URL(`${targetPathname}${requestUrl.search}`, normalizeTargetBaseUrl(targetBaseUrl))
  const transport = targetUrl.protocol === 'https:' ? httpsRequest : httpRequest
  const agent = stateKey === 'oss'
    ? (targetUrl.protocol === 'https:' ? ossHttpsAgent : ossHttpAgent)
    : gatewayAgent
  const headers = {
    ...req.headers,
    host: targetUrl.host,
    'x-forwarded-host': req.headers.host || '',
    'x-forwarded-proto': requestUrl.protocol.replace(':', ''),
    'x-forwarded-for': [req.headers['x-forwarded-for'], req.socket.remoteAddress]
      .filter(Boolean)
      .join(', ')
  }

  delete headers.connection
  delete headers['keep-alive']
  delete headers['proxy-authorization']
  delete headers['proxy-authenticate']
  delete headers.te
  delete headers.trailer
  delete headers['transfer-encoding']
  delete headers.upgrade

  const upstream = transport(targetUrl, {
    method: req.method,
    headers,
    agent
  }, upstreamRes => {
    const responseHeaders = { ...upstreamRes.headers }
    delete responseHeaders['transfer-encoding']
    delete responseHeaders.connection
    if (cacheControl) {
      responseHeaders['cache-control'] = cacheControl
    }
    res.writeHead(upstreamRes.statusCode || 502, responseHeaders)
    upstreamRes.pipe(res)
    upstreamRes.on('end', release)
    upstreamRes.on('error', release)
  })

  upstream.setTimeout(proxyTimeoutMs, () => {
    upstream.destroy(new Error(`upstream timed out after ${proxyTimeoutMs}ms`))
  })

  req.on('aborted', () => upstream.destroy())

  res.on('close', () => {
    if (!res.writableEnded) {
      upstream.destroy()
    }
    release()
  })

  upstream.on('error', error => {
    release()
    if (!res.writableEnded) {
      writeJsonError(res, 502, error?.message || 'bad gateway')
    }
  })

  req.pipe(upstream)
}

const server = createServer(async (req, res) => {
  try {
    const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`)
    const pathname = decodeURIComponent(url.pathname)

    if (pathname === '/health') {
      res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' })
      res.end('ok')
      return
    }

    if (pathname === '/api' || pathname.startsWith('/api/') || pathname === '/uploads' || pathname.startsWith('/uploads/')) {
      proxyRequest(req, res, gatewayUrl, { stateKey: 'gateway', maxInFlight: maxGatewayProxyRequests })
      return
    }

    if (pathname === '/oss' || pathname.startsWith('/oss/')) {
      proxyRequest(req, res, ossProxyTarget, {
        stripPrefix: '/oss',
        cacheControl: 'public, max-age=86400',
        stateKey: 'oss',
        maxInFlight: maxOssProxyRequests
      })
      return
    }

    const filePath = pathname === '/' ? '/index.html' : pathname

    try {
      await serveFile(filePath, res)
    } catch {
      await serveFile('/index.html', res)
    }
  } catch (error) {
    res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end(error?.message || 'internal server error')
  }
})

server.keepAliveTimeout = 65000
server.headersTimeout = 66000
server.requestTimeout = 30000

server.listen(port, '0.0.0.0', () => {
  console.log(`Static server running on port ${port}`)
})

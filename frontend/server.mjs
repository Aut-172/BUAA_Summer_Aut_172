import { createServer } from 'http'
import { request as httpRequest } from 'http'
import { request as httpsRequest } from 'https'
import { readFile } from 'fs/promises'
import { extname, resolve } from 'path'

const rootDir = resolve('/usr/share/nginx/html')
const port = Number(process.env.PORT || 80)
const gatewayUrl = process.env.API_GATEWAY_URL || 'http://api-gateway:8080'

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

function proxyRequest(req, res, targetBaseUrl) {
  const requestUrl = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`)
  const targetUrl = new URL(`${requestUrl.pathname}${requestUrl.search}`, targetBaseUrl)
  const transport = targetUrl.protocol === 'https:' ? httpsRequest : httpRequest
  const headers = {
    ...req.headers,
    host: targetUrl.host,
    'x-forwarded-host': req.headers.host || '',
    'x-forwarded-proto': requestUrl.protocol.replace(':', ''),
    'x-forwarded-for': [req.headers['x-forwarded-for'], req.socket.remoteAddress]
      .filter(Boolean)
      .join(', ')
  }

  const upstream = transport(targetUrl, {
    method: req.method,
    headers
  }, upstreamRes => {
    const responseHeaders = { ...upstreamRes.headers }
    delete responseHeaders['transfer-encoding']
    res.writeHead(upstreamRes.statusCode || 502, responseHeaders)
    upstreamRes.pipe(res)
  })

  upstream.on('error', error => {
    if (!res.headersSent) {
      res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' })
    }
    res.end(JSON.stringify({ code: 502, message: error?.message || 'bad gateway', data: null }))
  })

  req.pipe(upstream)
}

createServer(async (req, res) => {
  try {
    const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`)
    const pathname = decodeURIComponent(url.pathname)

    if (pathname === '/health') {
      res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' })
      res.end('ok')
      return
    }

    if (pathname === '/api' || pathname.startsWith('/api/') || pathname.startsWith('/uploads/')) {
      proxyRequest(req, res, gatewayUrl)
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
}).listen(port, '0.0.0.0', () => {
  console.log(`Static server running on port ${port}`)
})

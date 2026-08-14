#!/usr/bin/env node

import { randomBytes, timingSafeEqual } from 'node:crypto'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import http from 'node:http'
import net from 'node:net'
import os from 'node:os'

const options = parseArguments(process.argv.slice(2))
const upstream = new URL(options.upstream)
if (upstream.protocol !== 'http:') fail('桥接器的本机 upstream 必须使用 HTTP，例如 http://127.0.0.1:3080')

const pairingToken = options.token || randomBytes(24).toString('base64url')
const cookieName = 'open_harness_pairing'
const server = http.createServer((request, response) => {
  handleRequest(request, response).catch((error) => {
    console.error(error)
    if (!response.headersSent) response.writeHead(502)
    response.end('bridge error')
  })
})

server.on('upgrade', (request, socket, head) => {
  const requestURL = new URL(request.url || '/', 'http://bridge.local')
  if (!isAuthorized(request, requestURL)) {
    socket.end('HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n')
    return
  }
  const upstreamSocket = net.connect(Number(upstream.port || 80), upstream.hostname)
  upstreamSocket.once('connect', () => {
    const lines = [`${request.method || 'GET'} ${requestURL.pathname}${requestURL.search} HTTP/1.1`]
    for (const [name, value] of Object.entries(forwardedHeaders(request))) {
      if (value !== undefined) lines.push(`${name}: ${Array.isArray(value) ? value.join(', ') : value}`)
    }
    upstreamSocket.write(`${lines.join('\r\n')}\r\n\r\n`)
    if (head.length > 0) upstreamSocket.write(head)
    socket.pipe(upstreamSocket).pipe(socket)
  })
  upstreamSocket.on('error', () => socket.destroy())
  socket.on('error', () => upstreamSocket.destroy())
})

server.listen(options.port, options.host, () => {
  const localHost = os.hostname().endsWith('.local') ? os.hostname() : `${os.hostname()}.local`
  const candidates = options.publicUrl
    ? [pairingURLFor(options.publicUrl, pairingToken)]
    : [
        `http://${localHost}:${options.port}/?token=${pairingToken}`,
        ...lanAddresses().map((address) => `http://${address}:${options.port}/?token=${pairingToken}`),
      ]
  console.log('\n小鲸鱼电脑桥接器已启动')
  console.log(`上游 Harness：${upstream.origin}`)
  console.log('手机和电脑连入同一 Wi-Fi，在安卓端打开“设置 → 电脑 Harness”扫码：')
  candidates.forEach((url) => console.log(`  ${url}`))
  printPairingQRCode(candidates[0])
  console.log('请勿分享配对码，也不要将 3081 端口直接暴露到公网。')
})

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)))
}

async function handleRequest(request, response) {
  const requestURL = new URL(request.url || '/', 'http://bridge.local')
  const queryToken = requestURL.searchParams.get('token')
  if (requestURL.pathname !== '/__open_harness_mobile/health' && queryToken && tokenMatches(queryToken)) {
    requestURL.searchParams.delete('token')
    response.writeHead(302, {
      'Cache-Control': 'no-store',
      'Set-Cookie': `${cookieName}=${pairingToken}; HttpOnly; SameSite=Strict; Path=/`,
      Location: `${requestURL.pathname}${requestURL.search}`,
    })
    response.end()
    return
  }
  if (!isAuthorized(request, requestURL)) {
    response.writeHead(401, { 'Cache-Control': 'no-store', 'Content-Type': 'text/plain; charset=utf-8' })
    response.end('需要有效的小鲸鱼配对令牌。')
    return
  }
  if (requestURL.pathname === '/__open_harness_mobile/health') {
    const upstreamStatus = await probeUpstream()
    response.writeHead(upstreamStatus > 0 ? 200 : 502, {
      'Cache-Control': 'no-store',
      'Content-Type': 'application/json; charset=utf-8',
    })
    response.end(JSON.stringify({
      bridge: 'ok',
      upstream: upstreamStatus > 0 ? 'ok' : 'unreachable',
      upstreamStatus,
    }))
    return
  }
  proxyHTTP(request, response, requestURL)
}

function proxyHTTP(request, response, requestURL) {
  const proxyRequest = http.request({
    hostname: upstream.hostname,
    port: Number(upstream.port || 80),
    method: request.method,
    path: `${requestURL.pathname}${requestURL.search}`,
    headers: forwardedHeaders(request),
  }, (proxyResponse) => {
    response.writeHead(proxyResponse.statusCode || 502, proxyResponse.headers)
    proxyResponse.pipe(response)
  })
  proxyRequest.on('error', (error) => {
    if (!response.headersSent) response.writeHead(502)
    response.end(`无法连接电脑上的 DeepSeek Harness：${error.message}`)
  })
  request.pipe(proxyRequest)
}

function forwardedHeaders(request) {
  const headers = { ...request.headers, host: upstream.host }
  delete headers.cookie
  if (headers.origin) headers.origin = upstream.origin
  return headers
}

function probeUpstream() {
  return new Promise((resolveProbe) => {
    const request = http.get(upstream, { headers: { Host: upstream.host } }, (response) => {
      response.resume()
      resolveProbe(response.statusCode || 0)
    })
    request.setTimeout(3000, () => request.destroy())
    request.on('error', () => resolveProbe(0))
  })
}

function isAuthorized(request, requestURL) {
  const bearer = request.headers.authorization?.match(/^Bearer (.+)$/)?.[1]
  const cookie = request.headers.cookie?.split(';').map((item) => item.trim().split('='))
    .find(([name]) => name === cookieName)?.[1]
  return tokenMatches(requestURL.searchParams.get('token')) || tokenMatches(bearer) || tokenMatches(cookie)
}

function tokenMatches(candidate) {
  if (!candidate) return false
  const expected = Buffer.from(pairingToken)
  const actual = Buffer.from(candidate)
  return expected.length === actual.length && timingSafeEqual(expected, actual)
}

function lanAddresses() {
  const addresses = []
  for (const entries of Object.values(os.networkInterfaces())) {
    for (const entry of entries || []) {
      if (entry.family === 'IPv4' && !entry.internal) addresses.push(entry.address)
    }
  }
  return [...new Set(addresses)]
}

function printPairingQRCode(pairingURL) {
  const qrencode = spawnSync('qrencode', ['-t', 'ANSIUTF8', '-m', '1', pairingURL], { encoding: 'utf8' })
  if (qrencode.status === 0 && qrencode.stdout) {
    process.stdout.write(`\n${qrencode.stdout}\n`)
    return
  }
  if (process.platform === 'darwin') {
    const script = resolve(dirname(fileURLToPath(import.meta.url)), 'make-qr.swift')
    const generated = spawnSync('swift', [script, pairingURL], { encoding: 'utf8' })
    const imagePath = generated.stdout?.trim()
    if (generated.status === 0 && imagePath) {
      console.log(`\n配对二维码：${imagePath}`)
      spawnSync('open', [imagePath])
      return
    }
  }
  console.log('\n未能自动显示二维码，请在手机端手动输入上方局域网地址和令牌。')
}

function pairingURLFor(baseURL, token) {
  let url
  try { url = new URL(baseURL) } catch { fail('--public-url 必须是完整的 HTTP 或 HTTPS 地址') }
  if (!['http:', 'https:'].includes(url.protocol) || !url.hostname) fail('--public-url 必须是完整的 HTTP 或 HTTPS 地址')
  url.pathname = '/'
  url.search = ''
  url.hash = ''
  url.searchParams.set('token', token)
  return url.toString()
}

function parseArguments(argumentsList) {
  const result = { host: '0.0.0.0', port: 3081, upstream: 'http://127.0.0.1:3080', token: '', publicUrl: '' }
  for (let index = 0; index < argumentsList.length; index += 1) {
    const argument = argumentsList[index]
    if (argument === '--help') {
      console.log('node bridge.mjs [--port 3081] [--upstream URL] [--public-url URL] [--token TOKEN]')
      process.exit(0)
    }
    if (!['--host', '--port', '--upstream', '--token', '--public-url'].includes(argument)) fail(`未知参数：${argument}`)
    const value = argumentsList[index + 1]
    if (!value) fail(`${argument} 缺少值`)
    index += 1
    if (argument === '--port') result.port = Number(value)
    else if (argument === '--public-url') result.publicUrl = value
    else result[argument.slice(2)] = value
  }
  if (!Number.isInteger(result.port) || result.port < 1 || result.port > 65535) fail('端口必须是 1 到 65535 的整数')
  return result
}

function fail(message) {
  console.error(message)
  process.exit(1)
}

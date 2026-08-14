import { createHash, randomBytes, randomInt, timingSafeEqual } from 'node:crypto'
import { createServer } from 'node:http'
import { networkInterfaces } from 'node:os'

export const name = 'whale-mobile-companion'
export const inject = ['apiProxy']

const PRODUCT = 'whale-harness-mobile'
const PROTOCOL = 1
const MAX_BODY_BYTES = 1024 * 1024
const PAIRING_TTL_MS = 10 * 60 * 1000
const FAILURE_WINDOW_MS = 60 * 1000
const MAX_PAIRING_FAILURES = 5

const RPC_ROUTES = new Map([
  ['session.list', api => api.sessions.list.bind(api.sessions)],
  ['session.create', api => api.sessions.create.bind(api.sessions)],
  ['session.history', api => api.sessions.history.bind(api.sessions)],
  ['session.prompt', api => api.sessions.prompt.bind(api.sessions)],
  ['session.cancel', api => api.sessions.cancel.bind(api.sessions)],
  ['workspace.list', api => api.workspace.list.bind(api.workspace)],
  ['skill.list', api => api.skills.list.bind(api.skills)],
])

export async function apply(ctx, config = {}) {
  const companion = await createMobileCompanionServer({
    apiProxy: ctx.apiProxy,
    host: config.host ?? '0.0.0.0',
    port: config.port ?? 43117,
    printPairing: true,
  })
  ctx.effect(() => () => companion.close(), 'whale-mobile-companion.server')
}

export async function createMobileCompanionServer({
  apiProxy,
  host = '0.0.0.0',
  port = 43117,
  printPairing = false,
  pairingCode,
} = {}) {
  if (!apiProxy) throw new Error('whale-mobile-companion requires ctx.apiProxy')

  const state = {
    pairing: createPairing(pairingCode),
    deviceTokenHashes: new Set(),
    failures: new Map(),
  }
  const server = createServer((request, response) => {
    void handleRequest({ apiProxy, state, request, response }).catch(error => {
      if (!response.headersSent) writeJson(response, 500, { error: 'internal', message: String(error) })
      else response.destroy()
    })
  })

  await new Promise((resolve, reject) => {
    const onError = error => {
      server.off('listening', onListening)
      reject(error)
    }
    const onListening = () => {
      server.off('error', onError)
      resolve()
    }
    server.once('error', onError)
    server.once('listening', onListening)
    server.listen(port, host)
  })

  const address = server.address()
  if (address === null || typeof address === 'string') throw new Error('mobile companion did not bind a TCP port')
  const endpoint = publicEndpoint(host, address.port)
  const payload = pairingPayload(endpoint, state.pairing.code)
  if (printPairing) await printPairingInstructions(payload, endpoint, state.pairing.code)

  return {
    endpoint,
    pairingCode: state.pairing.code,
    pairingPayload: payload,
    close: () => new Promise(resolve => {
      server.close(() => resolve())
      server.closeAllConnections?.()
    }),
  }
}

async function handleRequest({ apiProxy, state, request, response }) {
  const url = new URL(request.url ?? '/', 'http://whale-harness.local')
  response.setHeader('Cache-Control', 'no-store')
  response.setHeader('X-Content-Type-Options', 'nosniff')

  if (request.method === 'POST' && url.pathname === '/v1/pair') {
    const remoteAddress = request.socket.remoteAddress ?? 'unknown'
    if (isRateLimited(state, remoteAddress)) {
      writeJson(response, 429, { error: 'too-many-attempts', message: '配对尝试过多，请稍后再试' })
      return
    }
    const body = await readJson(request)
    const submitted = typeof body.code === 'string' ? body.code.trim() : ''
    if (Date.now() > state.pairing.expiresAt || !safeEqual(submitted, state.pairing.code)) {
      recordFailure(state, remoteAddress)
      writeJson(response, 401, { error: 'pairing-denied', message: '配对码无效或已过期' })
      return
    }
    const token = randomBytes(32).toString('base64url')
    state.deviceTokenHashes.add(hashToken(token))
    state.failures.delete(remoteAddress)
    state.pairing = createPairing()
    writeJson(response, 200, {
      product: PRODUCT,
      protocol: PROTOCOL,
      token,
      deviceName: typeof body.deviceName === 'string' ? body.deviceName.slice(0, 80) : 'Android',
    })
    return
  }

  const token = bearerToken(request.headers.authorization)
  if (!token || !state.deviceTokenHashes.has(hashToken(token))) {
    writeJson(response, 401, { error: 'unauthorized', message: '请先用 Harness 显示的二维码或配对码连接' })
    return
  }

  if (request.method === 'GET' && url.pathname === '/v1/health') {
    writeJson(response, 200, { product: PRODUCT, protocol: PROTOCOL, harness: 'ready' })
    return
  }

  if (request.method === 'DELETE' && url.pathname === '/v1/device') {
    state.deviceTokenHashes.delete(hashToken(token))
    writeJson(response, 200, { revoked: true })
    return
  }

  if (request.method === 'POST' && url.pathname.startsWith('/api/')) {
    const method = decodeURIComponent(url.pathname.slice('/api/'.length))
    const route = RPC_ROUTES.get(method)
    if (!route) {
      writeJson(response, 404, { error: 'unsupported-method', message: `移动端尚未开放 ${method}` })
      return
    }
    const envelope = await readJson(request)
    if (envelope.type !== 'client-request' || envelope.method !== method || typeof envelope.rpcId !== 'string') {
      writeJson(response, 400, { error: 'bad-request', message: 'RPC 请求格式无效' })
      return
    }
    const narrow = await route(apiProxy)({ rpcId: envelope.rpcId, payload: envelope.payload ?? {} })
    writeJson(response, 200, {
      type: 'server-response',
      rpcId: narrow.rpcId,
      result: narrow.result,
    })
    return
  }

  writeJson(response, 404, { error: 'not-found' })
}

function createPairing(fixedCode) {
  return {
    code: fixedCode ?? String(randomInt(0, 100_000_000)).padStart(8, '0'),
    expiresAt: Date.now() + PAIRING_TTL_MS,
  }
}

function pairingPayload(endpoint, code) {
  const url = new URL('whaleharness://pair')
  url.searchParams.set('endpoint', endpoint)
  url.searchParams.set('code', code)
  url.searchParams.set('v', String(PROTOCOL))
  return url.toString()
}

function publicEndpoint(host, port) {
  const publicHost = host === '0.0.0.0' ? firstLanIpv4() : host
  return `http://${publicHost}:${port}`
}

function firstLanIpv4() {
  for (const addresses of Object.values(networkInterfaces())) {
    for (const address of addresses ?? []) {
      if (address.family === 'IPv4' && !address.internal) return address.address
    }
  }
  return '127.0.0.1'
}

async function printPairingInstructions(payload, endpoint, code) {
  console.log('\n🐳 Whale Harness 移动直连已启动（与 DeepSeek Harness 同一进程）')
  console.log(`电脑地址：${endpoint}`)
  console.log(`一次性配对码：${code}（10 分钟有效）`)
  try {
    const module = await import('qrcode-terminal')
    const qr = module.default ?? module
    qr.generate(payload, { small: true }, value => console.log(`\n${value}`))
  } catch {
    console.log(`配对内容：${payload}`)
  }
}

async function readJson(request) {
  const chunks = []
  let bytes = 0
  for await (const chunk of request) {
    bytes += chunk.length
    if (bytes > MAX_BODY_BYTES) throw new Error('request body too large')
    chunks.push(chunk)
  }
  const text = Buffer.concat(chunks).toString('utf8')
  return text === '' ? {} : JSON.parse(text)
}

function writeJson(response, status, value) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' })
  response.end(JSON.stringify(value))
}

function bearerToken(header) {
  if (typeof header !== 'string' || !header.startsWith('Bearer ')) return ''
  return header.slice('Bearer '.length).trim()
}

function hashToken(token) {
  return createHash('sha256').update(token).digest('hex')
}

function safeEqual(left, right) {
  const a = Buffer.from(left)
  const b = Buffer.from(right)
  return a.length === b.length && timingSafeEqual(a, b)
}

function isRateLimited(state, address) {
  const recent = (state.failures.get(address) ?? []).filter(time => Date.now() - time < FAILURE_WINDOW_MS)
  state.failures.set(address, recent)
  return recent.length >= MAX_PAIRING_FAILURES
}

function recordFailure(state, address) {
  const recent = state.failures.get(address) ?? []
  state.failures.set(address, [...recent, Date.now()])
}

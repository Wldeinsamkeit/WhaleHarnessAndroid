import assert from 'node:assert/strict'
import test from 'node:test'
import { createMobileCompanionServer } from '../index.js'

function ok(rpcId, value) {
  return Promise.resolve({ rpcId, result: { ok: true, value } })
}

function fakeApi() {
  return {
    sessions: {
      list: request => ok(request.rpcId, { items: [{ sessionId: 'session-1', updatedAt: 1, running: false, blank: false }] }),
      create: request => ok(request.rpcId, { sessionId: 'session-new' }),
      history: request => ok(request.rpcId, { events: [], hasMore: false }),
      prompt: request => ok(request.rpcId, { accepted: true }),
      cancel: request => ok(request.rpcId, { accepted: true }),
    },
    workspace: { list: request => ok(request.rpcId, { items: [] }) },
    skills: { list: request => ok(request.rpcId, { items: [] }) },
  }
}

async function pair(endpoint, code) {
  const response = await fetch(`${endpoint}/v1/pair`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ code, deviceName: 'test phone' }),
  })
  return { response, body: await response.json() }
}

test('one-time pairing authorizes direct Harness RPC calls', async t => {
  const companion = await createMobileCompanionServer({
    apiProxy: fakeApi(),
    host: '127.0.0.1',
    port: 0,
    pairingCode: '12345678',
  })
  t.after(() => companion.close())

  const denied = await pair(companion.endpoint, '00000000')
  assert.equal(denied.response.status, 401)

  const paired = await pair(companion.endpoint, '12345678')
  assert.equal(paired.response.status, 200)
  assert.equal(paired.body.product, 'whale-harness-mobile')
  assert.ok(paired.body.token)

  const reused = await pair(companion.endpoint, '12345678')
  assert.equal(reused.response.status, 401)

  const response = await fetch(`${companion.endpoint}/api/session.list`, {
    method: 'POST',
    headers: {
      authorization: `Bearer ${paired.body.token}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      type: 'client-request',
      rpcId: 'rpc-1',
      method: 'session.list',
      payload: {},
    }),
  })
  const body = await response.json()
  assert.equal(response.status, 200)
  assert.equal(body.type, 'server-response')
  assert.equal(body.result.value.items[0].sessionId, 'session-1')
})

test('device token can be revoked', async t => {
  const companion = await createMobileCompanionServer({
    apiProxy: fakeApi(),
    host: '127.0.0.1',
    port: 0,
    pairingCode: '87654321',
  })
  t.after(() => companion.close())
  const { body } = await pair(companion.endpoint, '87654321')

  const revoked = await fetch(`${companion.endpoint}/v1/device`, {
    method: 'DELETE',
    headers: { authorization: `Bearer ${body.token}` },
  })
  assert.equal(revoked.status, 200)

  const health = await fetch(`${companion.endpoint}/v1/health`, {
    headers: { authorization: `Bearer ${body.token}` },
  })
  assert.equal(health.status, 401)
})

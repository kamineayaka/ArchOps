#!/usr/bin/env node
/**
 * ArchOps functional smoke tests against a running stack (e.g. local Compose).
 *
 * Usage:
 *   node deploy/scripts/smoke-functional.mjs
 *   ARCHOPS_BASE_URL=http://127.0.0.1 node deploy/scripts/smoke-functional.mjs
 *
 * Env:
 *   ARCHOPS_BASE_URL   default http://127.0.0.1
 *   ARCHOPS_USER       default admin
 *   ARCHOPS_PASSWORD   default admin123
 *   ARCHOPS_TIMEOUT_MS default 15000
 */

const BASE_URL = (process.env.ARCHOPS_BASE_URL || 'http://127.0.0.1').replace(/\/$/, '')
const USERNAME = process.env.ARCHOPS_USER || 'admin'
const PASSWORD = process.env.ARCHOPS_PASSWORD || 'admin123'
const TIMEOUT_MS = Number(process.env.ARCHOPS_TIMEOUT_MS || 15000)

/** @type {{ name: string, ok: boolean, detail: string }[]} */
const results = []

function record(name, ok, detail = '') {
  results.push({ name, ok, detail })
  const mark = ok ? 'PASS' : 'FAIL'
  console.log(`${mark.padEnd(4)} ${name}${detail ? ` — ${detail}` : ''}`)
}

async function request(path, { method = 'GET', token, body, expectStatus } = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
  try {
    const headers = { Accept: 'application/json' }
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    if (token) headers.Authorization = `Bearer ${token}`
    const res = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    })
    const text = await res.text()
    let json = null
    if (text) {
      try {
        json = JSON.parse(text)
      } catch {
        json = null
      }
    }
    if (expectStatus !== undefined && res.status !== expectStatus) {
      throw new Error(`HTTP ${res.status} (expected ${expectStatus}): ${text.slice(0, 200)}`)
    }
    return { status: res.status, json, text }
  } finally {
    clearTimeout(timer)
  }
}

async function check(name, fn) {
  try {
    const detail = await fn()
    record(name, true, typeof detail === 'string' ? detail : '')
  } catch (err) {
    record(name, false, err instanceof Error ? err.message : String(err))
  }
}

function assertApiOk(json, label) {
  if (!json || json.success !== true) {
    throw new Error(`${label}: success!=true code=${json?.code} message=${json?.message}`)
  }
  return json.data
}

async function main() {
  console.log(`ArchOps smoke → ${BASE_URL} as ${USERNAME}`)
  console.log('')

  let accessToken = ''
  let refreshToken = ''

  await check('health/liveness', async () => {
    const { status, text } = await request('/actuator/health/liveness')
    if (status !== 200) throw new Error(`HTTP ${status}: ${text.slice(0, 120)}`)
    return text.slice(0, 80)
  })

  await check('health/readiness', async () => {
    const { status, text } = await request('/actuator/health/readiness')
    if (status !== 200) throw new Error(`HTTP ${status}: ${text.slice(0, 120)}`)
    return text.slice(0, 80)
  })

  await check('auth/login', async () => {
    const { json } = await request('/api/auth/login', {
      method: 'POST',
      body: { username: USERNAME, password: PASSWORD },
      expectStatus: 200,
    })
    const data = assertApiOk(json, 'login')
    if (!data?.accessToken || !data?.refreshToken) throw new Error('missing tokens')
    accessToken = data.accessToken
    refreshToken = data.refreshToken
    return `user=${data.user?.username} roles=${(data.user?.roles || []).join(',')}`
  })

  await check('auth/me', async () => {
    const { json } = await request('/api/auth/me', { token: accessToken, expectStatus: 200 })
    const data = assertApiOk(json, 'me')
    if (data?.username !== USERNAME) throw new Error(`username mismatch: ${data?.username}`)
    return data.username
  })

  await check('assets/list', async () => {
    const { json } = await request('/api/assets', { token: accessToken, expectStatus: 200 })
    const data = assertApiOk(json, 'assets')
    if (!Array.isArray(data)) throw new Error('expected array')
    return `count=${data.length}`
  })

  await check('asset-types/list', async () => {
    const { json } = await request('/api/asset-types', { token: accessToken, expectStatus: 200 })
    const data = assertApiOk(json, 'asset-types')
    if (!Array.isArray(data) || data.length === 0) throw new Error('expected non-empty asset types')
    return `count=${data.length}`
  })

  await check('graph/meta', async () => {
    const { json } = await request('/api/graph/meta', { token: accessToken, expectStatus: 200 })
    const data = assertApiOk(json, 'graph/meta')
    if (data?.partitionKey !== 'graph:global') throw new Error(`unexpected partitionKey=${data?.partitionKey}`)
    return `graphVersion=${data.graphVersion}`
  })

  await check('graph/snapshot', async () => {
    const { json } = await request('/api/graph/snapshot', { token: accessToken, expectStatus: 200 })
    const data = assertApiOk(json, 'graph/snapshot')
    return `nodes=${Array.isArray(data?.nodes) ? data.nodes.length : '?'} edges=${Array.isArray(data?.edges) ? data.edges.length : '?'}`
  })

  await check('architecture/partitions', async () => {
    const { json } = await request('/api/architecture/partitions', { token: accessToken, expectStatus: 200 })
    const data = assertApiOk(json, 'partitions')
    if (!Array.isArray(data)) throw new Error('expected array')
    return `count=${data.length}`
  })

  await check('architecture/partitions/view', async () => {
    const { json } = await request('/api/architecture/partitions/view', { token: accessToken, expectStatus: 200 })
    assertApiOk(json, 'partitions/view')
  })

  await check('architecture/proposals', async () => {
    const { json } = await request('/api/architecture/proposals', { token: accessToken, expectStatus: 200 })
    const data = assertApiOk(json, 'proposals')
    if (!Array.isArray(data)) throw new Error('expected array')
    return `count=${data.length}`
  })

  await check('approvals/pending', async () => {
    const { json } = await request('/api/approvals/pending', { token: accessToken, expectStatus: 200 })
    const data = assertApiOk(json, 'approvals/pending')
    if (!Array.isArray(data)) throw new Error('expected array')
    return `count=${data.length}`
  })

  await check('approvals/mine', async () => {
    const { json } = await request('/api/approvals/mine', { token: accessToken, expectStatus: 200 })
    const data = assertApiOk(json, 'approvals/mine')
    if (!Array.isArray(data)) throw new Error('expected array')
    return `count=${data.length}`
  })

  await check('knowledge/index-stats', async () => {
    const { json } = await request('/api/knowledge/index-stats', { token: accessToken, expectStatus: 200 })
    assertApiOk(json, 'index-stats')
  })

  await check('knowledge/architecture', async () => {
    const { status, json } = await request('/api/knowledge/architecture', { token: accessToken })
    if (status !== 200) throw new Error(`HTTP ${status}`)
    // null architecture is valid on empty installs
    if (json && json.success !== true) throw new Error(`code=${json.code} message=${json.message}`)
  })

  await check('knowledge/work-logs', async () => {
    const { json } = await request('/api/knowledge/work-logs', { token: accessToken, expectStatus: 200 })
    const data = assertApiOk(json, 'work-logs')
    if (!Array.isArray(data)) throw new Error('expected array')
    return `count=${data.length}`
  })

  await check('audit/list', async () => {
    const { json } = await request('/api/audit?page=0&size=5', { token: accessToken, expectStatus: 200 })
    assertApiOk(json, 'audit')
  })

  await check('audit/verify', async () => {
    const { json } = await request('/api/audit/verify', { token: accessToken, expectStatus: 200 })
    const data = assertApiOk(json, 'audit/verify')
    return `chainOk=${data}`
  })

  await check('ai/providers', async () => {
    const { json } = await request('/api/ai/providers', { token: accessToken, expectStatus: 200 })
    const data = assertApiOk(json, 'providers')
    if (!Array.isArray(data)) throw new Error('expected array')
    return `enabled=${data.length}`
  })

  await check('ai/model-defaults', async () => {
    const { json } = await request('/api/ai/model-defaults?model=gpt-4o', { token: accessToken, expectStatus: 200 })
    assertApiOk(json, 'model-defaults')
  })

  await check('ai/conversations', async () => {
    const { json } = await request('/api/ai/conversations', { token: accessToken, expectStatus: 200 })
    const data = assertApiOk(json, 'conversations')
    if (!Array.isArray(data)) throw new Error('expected array')
    return `count=${data.length}`
  })

  await check('ai/settings', async () => {
    const { json } = await request('/api/ai/settings', { token: accessToken, expectStatus: 200 })
    assertApiOk(json, 'settings')
  })

  await check('ssh/pool', async () => {
    const { json } = await request('/api/ssh/pool', { token: accessToken, expectStatus: 200 })
    const data = assertApiOk(json, 'ssh/pool')
    if (!Array.isArray(data)) throw new Error('expected array')
    return `entries=${data.length}`
  })

  await check('terminal/dock', async () => {
    const { json } = await request('/api/terminal/dock', { token: accessToken, expectStatus: 200 })
    assertApiOk(json, 'terminal/dock')
  })

  await check('auth/refresh', async () => {
    const { json } = await request('/api/auth/refresh', {
      method: 'POST',
      body: { refreshToken },
      expectStatus: 200,
    })
    const data = assertApiOk(json, 'refresh')
    if (!data?.accessToken) throw new Error('missing accessToken')
    accessToken = data.accessToken
    refreshToken = data.refreshToken || refreshToken
  })

  await check('auth/logout', async () => {
    const { json } = await request('/api/auth/logout', {
      method: 'POST',
      token: accessToken,
      expectStatus: 200,
    })
    assertApiOk(json, 'logout')
  })

  await check('auth/me after logout → 401', async () => {
    const { status } = await request('/api/auth/me', { token: accessToken })
    if (status !== 401 && status !== 403) {
      throw new Error(`expected 401/403 after logout, got ${status}`)
    }
    return `HTTP ${status}`
  })

  await check('assets without token → 401', async () => {
    const { status } = await request('/api/assets')
    if (status !== 401 && status !== 403) {
      throw new Error(`expected 401/403, got ${status}`)
    }
    return `HTTP ${status}`
  })

  const failed = results.filter((r) => !r.ok)
  const passed = results.length - failed.length
  console.log('')
  console.log(`Summary: ${passed}/${results.length} passed`)
  if (failed.length) {
    console.log('Failed:')
    for (const f of failed) console.log(`  - ${f.name}: ${f.detail}`)
    process.exitCode = 1
  }
}

main().catch((err) => {
  console.error(err)
  process.exitCode = 1
})

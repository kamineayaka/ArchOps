import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { hasRole, isAdmin, isOperatorOrAdmin } from '../src/utils/roles.ts'
import { apiErrorMessage } from '../src/utils/apiError.ts'

describe('roles (BUG-03 regression)', () => {
  it('treats ADMIN and ROLE_ADMIN as equivalent', () => {
    assert.equal(isAdmin(['ADMIN']), true)
    assert.equal(isAdmin(['ROLE_ADMIN']), true)
    assert.equal(hasRole(['ROLE_OPERATOR'], 'OPERATOR'), true)
    assert.equal(isOperatorOrAdmin(['OPERATOR']), true)
    assert.equal(isAdmin(['OPERATOR']), false)
    assert.equal(isAdmin([]), false)
    assert.equal(isAdmin(null), false)
  })
})

describe('apiErrorMessage (BUG-01 regression)', () => {
  it('returns fallback for unknown errors', () => {
    assert.equal(apiErrorMessage('x', 'failed'), 'failed')
    assert.equal(apiErrorMessage(new Error('boom'), 'failed'), 'boom')
  })
})

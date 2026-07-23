/** Normalize API role names (`ADMIN`) and Spring-style (`ROLE_ADMIN`). */
export function hasRole(roles: string[] | undefined | null, role: string): boolean {
  if (!roles?.length) return false
  const want = role.startsWith('ROLE_') ? role.slice(5) : role
  return roles.some((r) => {
    const bare = r.startsWith('ROLE_') ? r.slice(5) : r
    return bare === want
  })
}

export function isAdmin(roles: string[] | undefined | null): boolean {
  return hasRole(roles, 'ADMIN')
}

export function isOperatorOrAdmin(roles: string[] | undefined | null): boolean {
  return hasRole(roles, 'ADMIN') || hasRole(roles, 'OPERATOR')
}

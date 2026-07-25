export function formatDateTime(value: string, locale = 'zh-CN') {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(date)
}

export function formatRole(role: string) {
  return role.replace(/^ROLE_/, '').replace(/_/g, ' ')
}

/** Minimal markdown: code fences + line breaks. No extra dependency. */
export function renderChatContent(content: string): string {
  const escaped = content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  return escaped
    .replace(/```([\s\S]*?)```/g, '<pre class="chat-code"><code>$1</code></pre>')
    .replace(/`([^`]+)`/g, '<code class="chat-inline-code">$1</code>')
    .replace(/\n/g, '<br>')
}

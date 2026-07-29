/** Blueprint Control Room — shared color tokens for CSS, Cytoscape, and xterm. */

export const aoColors = {
  ink: '#0B1220',
  slate: '#152033',
  steel: '#8BA3C7',
  blueprint: '#3D8BFF',
  blueprintHover: '#5BA0FF',
  blueprintPressed: '#2B6FD9',
  signal: '#E8A317',
  live: '#2BB673',
  error: '#E5484D',
  info: '#3D8BFF',
  white: '#FFFFFF',
  textOnDark: '#E8EEF7',
  textMutedOnDark: '#8BA3C7',
  edge: '#5A6F8C',
  canvasGrid: 'rgba(61, 139, 255, 0.07)',
} as const

/** Node kind colors — low-saturation blocks for topology / graph workbench. */
export const graphKindColors: Record<string, string> = {
  SERVER: '#3B6EA8',
  CLUSTER: '#6B5B95',
  SERVICE: '#4A7AB5',
  TAG: '#3A8A8A',
  ENVIRONMENT: '#3D8A6A',
  DATABASE: '#B8860B',
  NETWORK: '#5A6F8C',
  DEFAULT: '#3B6EA8',
}

export const xtermTheme = {
  background: aoColors.ink,
  foreground: aoColors.textOnDark,
  cursor: aoColors.blueprint,
  cursorAccent: aoColors.ink,
  selectionBackground: 'rgba(61, 139, 255, 0.35)',
  black: '#0B1220',
  red: aoColors.error,
  green: aoColors.live,
  yellow: aoColors.signal,
  blue: aoColors.blueprint,
  magenta: '#8B7EC8',
  cyan: '#5BA8C8',
  white: '#E8EEF7',
  brightBlack: '#5A6F8C',
  brightRed: '#FF6B6F',
  brightGreen: '#3DCA8A',
  brightYellow: '#F0B840',
  brightBlue: '#5BA0FF',
  brightMagenta: '#A99AE0',
  brightCyan: '#7EC4DE',
  brightWhite: '#FFFFFF',
} as const

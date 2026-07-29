import { aoColors, graphKindColors } from './tokens'

type CyStyle = {
  selector: string
  style: Record<string, string | number | boolean>
}

/** Shared Cytoscape stylesheet for Topology and GraphWorkbench. */
export function createGraphStylesheet(): CyStyle[] {
  return [
    {
      selector: 'node',
      style: {
        label: 'data(label)',
        'text-wrap': 'wrap',
        'text-valign': 'center',
        'text-halign': 'center',
        'font-size': 11,
        'font-family': 'IBM Plex Sans, sans-serif',
        color: aoColors.textOnDark,
        'background-color': graphKindColors.DEFAULT,
        'border-width': 1.5,
        'border-color': 'rgba(232, 238, 247, 0.35)',
        width: 62,
        height: 62,
        'text-outline-width': 2,
        'text-outline-color': aoColors.ink,
      },
    },
    {
      selector: 'node[kind = "CLUSTER"]',
      style: {
        'background-color': graphKindColors.CLUSTER,
        shape: 'round-rectangle',
        width: 78,
        height: 50,
      },
    },
    {
      selector: 'node[kind = "SERVICE"]',
      style: { 'background-color': graphKindColors.SERVICE },
    },
    {
      selector: 'node[kind = "TAG"]',
      style: {
        'background-color': graphKindColors.TAG,
        shape: 'diamond',
        width: 46,
        height: 46,
      },
    },
    {
      selector: 'node[kind = "ENVIRONMENT"]',
      style: {
        'background-color': graphKindColors.ENVIRONMENT,
        shape: 'round-rectangle',
        width: 70,
        height: 46,
      },
    },
    {
      selector: 'node[kind = "DATABASE"]',
      style: { 'background-color': graphKindColors.DATABASE },
    },
    {
      selector: 'node[kind = "NETWORK"]',
      style: {
        'background-color': graphKindColors.NETWORK,
        shape: 'hexagon',
        width: 50,
        height: 50,
      },
    },
    {
      selector: 'node:selected',
      style: {
        'border-width': 3,
        'border-color': aoColors.blueprint,
        'background-blacken': -0.05,
      },
    },
    {
      selector: 'node:active',
      style: {
        'overlay-opacity': 0.08,
        'overlay-color': aoColors.blueprint,
      },
    },
    {
      selector: 'edge',
      style: {
        width: 1.5,
        'line-color': aoColors.edge,
        'target-arrow-color': aoColors.edge,
        'target-arrow-shape': 'triangle',
        'arrow-scale': 0.85,
        'curve-style': 'bezier',
        label: 'data(label)',
        'font-size': 9,
        'font-family': 'JetBrains Mono, monospace',
        color: aoColors.textMutedOnDark,
        'text-rotation': 'autorotate',
        'text-background-opacity': 0.7,
        'text-background-color': aoColors.ink,
        'text-background-padding': '2px',
      },
    },
    {
      selector: 'edge:selected',
      style: {
        width: 2.5,
        'line-color': aoColors.blueprint,
        'target-arrow-color': aoColors.blueprint,
      },
    },
  ]
}

/** Extra classes for GraphWorkbench (draft / highlight / selection overlay). */
export function createWorkbenchStylesheet(): CyStyle[] {
  return [
    ...createGraphStylesheet(),
    {
      selector: '.dimmed',
      style: { opacity: 0.18 },
    },
    {
      selector: '.highlighted',
      style: {
        opacity: 1,
        'border-width': 3,
        'border-color': aoColors.signal,
        'line-color': aoColors.signal,
        'target-arrow-color': aoColors.signal,
        'z-index': 999,
      },
    },
    {
      selector: '.selected',
      style: {
        'border-width': 3,
        'border-color': aoColors.blueprint,
        'line-color': aoColors.blueprint,
        'target-arrow-color': aoColors.blueprint,
        'z-index': 1000,
      },
    },
    {
      selector: '.draft',
      style: {
        'border-style': 'dashed',
        'border-color': aoColors.live,
        'line-style': 'dashed',
        'line-color': aoColors.live,
      },
    },
    {
      selector: '.pending-delete',
      style: {
        opacity: 0.45,
        'border-color': aoColors.error,
        'line-color': aoColors.error,
      },
    },
  ]
}

import type { GlobalThemeOverrides } from 'naive-ui'

const primary = '#3D8BFF'
const primaryHover = '#5BA0FF'
const primaryPressed = '#2B6FD9'
const fontSans = "'IBM Plex Sans', system-ui, -apple-system, 'Segoe UI', sans-serif"

export const lightThemeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: primary,
    primaryColorHover: primaryHover,
    primaryColorPressed: primaryPressed,
    primaryColorSuppl: '#7AB0FF',
    successColor: '#2BB673',
    warningColor: '#E8A317',
    errorColor: '#E5484D',
    infoColor: primary,
    borderRadius: '6px',
    borderRadiusSmall: '4px',
    fontFamily: fontSans,
    fontFamilyMono: "'JetBrains Mono', ui-monospace, Menlo, monospace",
    fontWeightStrong: '600',
    fontSize: '14px',
    heightMedium: '34px',
    heightSmall: '28px',
  },
  Card: {
    borderRadius: '8px',
    paddingMedium: '16px 18px',
    titleFontSizeMedium: '15px',
    borderColor: '#D4DDE8',
  },
  Button: {
    borderRadiusMedium: '5px',
    borderRadiusSmall: '4px',
    heightMedium: '34px',
    heightSmall: '28px',
    fontWeight: '500',
  },
  Menu: {
    borderRadius: '5px',
    itemHeight: '36px',
    itemTextColorActive: primary,
    itemTextColorActiveHover: primaryHover,
    itemIconColorActive: primary,
    itemIconColorActiveHover: primaryHover,
  },
  DataTable: {
    borderRadius: '6px',
    thPaddingMedium: '8px 12px',
    tdPaddingMedium: '8px 12px',
    thFontWeight: '600',
    fontSizeMedium: '13px',
  },
  Tag: {
    borderRadius: '4px',
    heightSmall: '22px',
    fontSizeSmall: '12px',
  },
  Input: {
    borderRadius: '5px',
    heightMedium: '34px',
  },
  Select: {
    peers: {
      InternalSelection: {
        heightMedium: '34px',
        borderRadius: '5px',
      },
    },
  },
}

export const darkThemeOverrides: GlobalThemeOverrides = {
  ...lightThemeOverrides,
  common: {
    ...lightThemeOverrides.common,
    primaryColor: primary,
    primaryColorHover: primaryHover,
    primaryColorPressed: primaryPressed,
    primaryColorSuppl: '#7AB0FF',
    bodyColor: '#0B1220',
    cardColor: '#152033',
    modalColor: '#152033',
    popoverColor: '#1C2A40',
    tableColor: '#152033',
    inputColor: '#1C2A40',
    borderColor: '#243247',
    textColorBase: '#E8EEF7',
    textColor1: '#E8EEF7',
    textColor2: '#8BA3C7',
    textColor3: '#6B7F99',
  },
  Card: {
    ...lightThemeOverrides.Card,
    borderColor: '#243247',
    color: '#152033',
  },
  Menu: {
    ...lightThemeOverrides.Menu,
    itemTextColor: '#8BA3C7',
    itemTextColorHover: '#E8EEF7',
    itemTextColorActive: primary,
    itemTextColorActiveHover: primaryHover,
    itemIconColor: '#8BA3C7',
    itemIconColorHover: '#E8EEF7',
    itemIconColorActive: primary,
    itemColorActive: 'rgba(61, 139, 255, 0.12)',
    itemColorActiveHover: 'rgba(61, 139, 255, 0.16)',
  },
}

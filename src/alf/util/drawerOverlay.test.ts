import {createTheme, DEFAULT_PALETTE, utils} from '@bsky.app/alf'

import {getDrawerOverlayColor} from '#/alf/util/drawerOverlay'

describe('drawer overlay', () => {
  it.each([
    ['light', false, 'rgba(0, 57, 117, 0.1)'],
    ['dark', false, 'rgba(1, 82, 168, 0.1)'],
    ['dark', true, 'rgba(16, 133, 254, 0.1)'],
    ['dim', false, 'rgba(10, 13, 16, 0.8)'],
  ] as const)(
    'retains Bluesky scrim for %s (Android: %s)',
    (name, android, color) => {
      const theme = createTheme({
        name,
        scheme: name === 'light' ? 'light' : 'dark',
        palette: DEFAULT_PALETTE,
      })
      expect(getDrawerOverlayColor(theme, android)).toBe(color)
    },
  )

  it.each(['light', 'dark', 'dim'] as const)(
    'preserves custom canvas colors in %s mode',
    name => {
      const theme = createTheme({
        name,
        scheme: name === 'light' ? 'light' : 'dark',
        palette: {...DEFAULT_PALETTE, contrast_0: '#21150e'},
      })
      expect(getDrawerOverlayColor({...theme, surfaceRaised: '#33251e'})).toBe(
        utils.alpha('#21150e', name === 'dim' ? 0.8 : 0.1),
      )
    },
  )
})

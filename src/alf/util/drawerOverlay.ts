import {type Theme, utils} from '@bsky.app/alf'

/** Custom themes keep their palette; stock themes retain Bluesky's scrim. */
export function getDrawerOverlayColor(
  theme: Theme & {surfaceRaised?: string},
  isAndroid = false,
) {
  if (theme.surfaceRaised) {
    return utils.alpha(
      theme.palette.contrast_0,
      theme.name === 'dim' ? 0.8 : 0.1,
    )
  }
  return utils.select(theme.name, {
    light: 'rgba(0, 57, 117, 0.1)',
    dark: isAndroid ? 'rgba(16, 133, 254, 0.1)' : 'rgba(1, 82, 168, 0.1)',
    dim: 'rgba(10, 13, 16, 0.8)',
  })
}

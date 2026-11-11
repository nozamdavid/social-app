import {useKawaiiMode} from '#/state/preferences/kawaii'
import {IS_BLUESKY_PLUS} from '#/env'

export type LogoVariant = 'default' | 'kawaii'

export function useLogoVariant(allowVariants = true): LogoVariant {
  const kawaii = useKawaiiMode()

  if (IS_BLUESKY_PLUS || !allowVariants) return 'default'
  if (kawaii) return 'kawaii'
  return 'default'
}

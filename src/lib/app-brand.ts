import brand from '#/../assets/branding/caelum/brand.json'
import {IS_BLUESKY_PLUS} from '#/env'

export {default as CAELUM_BRAND} from '#/../assets/branding/caelum/brand.json'

/** App identity; Bluesky service, account and network names stay separate. */
export const APP_NAME = IS_BLUESKY_PLUS ? brand.name : 'Bluesky'

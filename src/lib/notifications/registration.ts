import {Platform} from 'react-native'

import {PUBLIC_APPVIEW_DID, PUBLIC_STAGING_APPVIEW_DID} from '#/lib/constants'
import {IS_BLUESKY_PLUS, PUSH_GATEWAY_DID} from '#/env'

/** Caelum push is opt-in, Android-only, and never uses upstream credentials. */
export const PUSH_ENABLED =
  !IS_BLUESKY_PLUS || (Platform.OS === 'android' && !!PUSH_GATEWAY_DID)

/** Keep registration and logout cleanup on the same service and application. */
export function getPushRegistration(accountService?: string) {
  return {
    serviceDid:
      PUSH_GATEWAY_DID ||
      (accountService?.includes('staging')
        ? PUBLIC_STAGING_APPVIEW_DID
        : PUBLIC_APPVIEW_DID),
    appId: IS_BLUESKY_PLUS ? 'am.noz.caelum' : 'xyz.blueskyweb.app',
  }
}

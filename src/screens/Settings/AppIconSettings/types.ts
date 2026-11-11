import type * as DynamicAppIcon from '@bsky.app/expo-dynamic-app-icon'

import {type ImageSource} from '#/components/Image'

export type AppIconSet = {
  id: DynamicAppIcon.IconName
  name: string
  iosImage: () => ImageSource
  androidImage: () => ImageSource
}

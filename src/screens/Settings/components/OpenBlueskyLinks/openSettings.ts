import {Linking} from 'react-native'
import {applicationId} from 'expo-application'
import {ActivityAction, startActivityAsync} from 'expo-intent-launcher'

/** Open the installed app's link settings, with app details as an OEM fallback. */
export async function openLinkSettings(): Promise<void> {
  if (applicationId) {
    try {
      await startActivityAsync(ActivityAction.APP_OPEN_BY_DEFAULT_SETTINGS, {
        data: `package:${applicationId}`,
      })
      return
    } catch {
      // Older Android versions and some OEMs only expose app details.
    }
  }
  await Linking.openSettings()
}

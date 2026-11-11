import {Alert, View} from 'react-native'
import {Trans, useLingui} from '@lingui/react/macro'

import {APP_NAME} from '#/lib/app-brand'
import * as SettingsList from '#/screens/Settings/components/SettingsList'
import {atoms as a, useTheme} from '#/alf'
import {ChainLink_Stroke2_Corner0_Rounded as ChainLinkIcon} from '#/components/icons/ChainLink'
import {Text} from '#/components/Typography'
import {IS_BLUESKY_PLUS} from '#/env'
import {openLinkSettings} from './openSettings'

export function OpenBlueskyLinks() {
  const {t: l} = useLingui()
  const t = useTheme()

  if (!IS_BLUESKY_PLUS) return null

  const onPress = async () => {
    try {
      await openLinkSettings()
    } catch {
      Alert.alert(
        l`Couldn’t open Android settings`,
        l`Open your device’s Settings app, find ${APP_NAME}, then choose “Open by default” to select bsky.app.`,
      )
    }
  }

  return (
    <SettingsList.PressableItem
      testID="openBlueskyLinksBtn"
      label={l`Open Bluesky links in ${APP_NAME}`}
      accessibilityHint={l`Opens Android settings so you can choose which app opens bsky.app links`}
      onPress={onPress}>
      <SettingsList.ItemIcon icon={ChainLinkIcon} />
      <View style={[a.flex_1, a.gap_2xs]}>
        <SettingsList.ItemText>
          <Trans>Open Bluesky links in {APP_NAME}</Trans>
        </SettingsList.ItemText>
        <Text style={[a.text_sm, t.atoms.text_contrast_medium]}>
          <Trans>
            In Android settings, enable “Open supported links” and select
            bsky.app. If the official Bluesky app already opens these links,
            turn off its supported links first. Setting names may vary by
            device.
          </Trans>
        </Text>
      </View>
      <SettingsList.Chevron />
    </SettingsList.PressableItem>
  )
}

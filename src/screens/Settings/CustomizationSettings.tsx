import {View} from 'react-native'
import {Trans, useLingui} from '@lingui/react/macro'
import {type NativeStackScreenProps} from '@react-navigation/native-stack'

import {type CommonNavigatorParams} from '#/lib/routes/types'
import {OpenBlueskyLinks} from '#/screens/Settings/components/OpenBlueskyLinks'
import * as SettingsList from '#/screens/Settings/components/SettingsList'
import {atoms as a, useTheme} from '#/alf'
import * as Toggle from '#/components/forms/Toggle'
import {LikeRepost_Stroke2_Corner2_Rounded as LikeRepostIcon} from '#/components/icons/LikeRepost'
import * as Layout from '#/components/Layout'
import {Text} from '#/components/Typography'
import {
  useAutoLikeReposts,
  useSetAutoLikeReposts,
} from '#/features/customization/preferences'

export function CustomizationSettingsScreen({}: NativeStackScreenProps<
  CommonNavigatorParams,
  'CustomizationSettings'
>) {
  const {t: l} = useLingui()
  const t = useTheme()
  const autoLikeReposts = useAutoLikeReposts()
  const setAutoLikeReposts = useSetAutoLikeReposts()

  return (
    <Layout.Screen testID="customizationSettingsScreen">
      <Layout.Header.Outer>
        <Layout.Header.BackButton />
        <Layout.Header.Content>
          <Layout.Header.TitleText>
            <Trans>Customization</Trans>
          </Layout.Header.TitleText>
        </Layout.Header.Content>
        <Layout.Header.Slot />
      </Layout.Header.Outer>
      <Layout.Content>
        <SettingsList.Container>
          <Toggle.Item
            name="auto-like-reposts"
            testID="autoLikeRepostsToggle"
            label={l`Like posts when reposting`}
            value={autoLikeReposts}
            onChange={setAutoLikeReposts}>
            <SettingsList.Item>
              <SettingsList.ItemIcon icon={LikeRepostIcon} />
              <View style={[a.flex_1, a.gap_2xs]}>
                <SettingsList.ItemText>
                  <Trans>Like posts when reposting</Trans>
                </SettingsList.ItemText>
                <Text style={t.atoms.text_contrast_medium}>
                  <Trans>
                    Automatically like posts you repost. Undoing a repost keeps
                    the like. Applies to all accounts on this device.
                  </Trans>
                </Text>
              </View>
              <Toggle.Platform />
            </SettingsList.Item>
          </Toggle.Item>
          <OpenBlueskyLinks />
        </SettingsList.Container>
      </Layout.Content>
    </Layout.Screen>
  )
}

import {View} from 'react-native'
import {Trans} from '@lingui/react/macro'
import {type NativeStackScreenProps} from '@react-navigation/native-stack'

import {type CommonNavigatorParams} from '#/lib/routes/types'
import {atoms as a, web} from '#/alf'
import * as Layout from '#/components/Layout'
import {Text} from '#/components/Typography'
import notices from '#/features/emojiSuggestions/notices.json'

export function EmojiCreditsScreen({}: NativeStackScreenProps<
  CommonNavigatorParams,
  'EmojiCredits'
>) {
  return (
    <Layout.Screen testID="emojiCreditsScreen">
      <Layout.Header.Outer>
        <Layout.Header.BackButton />
        <Layout.Header.Content>
          <Layout.Header.TitleText>
            <Trans>Emoji suggestion credits</Trans>
          </Layout.Header.TitleText>
        </Layout.Header.Content>
        <Layout.Header.Slot />
      </Layout.Header.Outer>
      <Layout.Content contentContainerStyle={[a.p_lg, a.gap_2xl]}>
        <Text>
          <Trans>
            Emoji suggestions use the following open-source projects. Their
            license notices are included below.
          </Trans>
        </Text>
        {notices.map(notice => (
          <View key={notice.name} style={[a.gap_md]}>
            <Text accessibilityRole="header" style={[a.text_lg, a.font_bold]}>
              {notice.name}
            </Text>
            <Text selectable>
              {notice.packages.join('\n')}
              {'\n'}
              {notice.license}
            </Text>
            <Text
              selectable
              style={[a.leading_relaxed, web({whiteSpace: 'pre-wrap'})]}>
              {notice.text}
            </Text>
          </View>
        ))}
      </Layout.Content>
    </Layout.Screen>
  )
}

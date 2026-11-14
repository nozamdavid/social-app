import {View} from 'react-native'
import {useLingui} from '@lingui/react/macro'

import {UserAvatar} from '#/view/com/util/UserAvatar'
import {useUsernameSuggestionsQuery} from '#/screens/Login/useUsernameSuggestionsQuery'
import {atoms as a, useTheme} from '#/alf'
import {Button} from '#/components/Button'
import {Text} from '#/components/Typography'

export function UsernameSuggestions({
  identifier,
  enabled,
  onSelect,
}: {
  identifier: string
  enabled: boolean
  onSelect: (handle: string) => void
}) {
  const t = useTheme()
  const {t: l} = useLingui()
  const accounts = useUsernameSuggestionsQuery(identifier, enabled)

  if (!accounts.length) return null

  return (
    <View
      testID="loginUsernameSuggestions"
      style={[a.mt_sm, a.border, a.rounded_md, t.atoms.border_contrast_low]}>
      {accounts.map(account => (
        <Button
          key={account.did}
          testID="loginUsernameSuggestion"
          label={l`Use @${account.handle}`}
          accessibilityHint={l`Fills in your username`}
          style={[a.flex_row, a.align_center, a.gap_sm, a.p_md, a.rounded_sm]}
          hoverStyle={t.atoms.bg_contrast_25}
          onPress={() => onSelect(account.handle)}>
          <UserAvatar type="user" size={36} avatar={account.avatar} />
          <View style={[a.flex_1, a.gap_2xs]}>
            {!!account.displayName && (
              <Text emoji numberOfLines={1} style={[a.font_bold]}>
                {account.displayName}
              </Text>
            )}
            <Text numberOfLines={1} style={[t.atoms.text_contrast_medium]}>
              @{account.handle}
            </Text>
          </View>
        </Button>
      ))}
    </View>
  )
}

import {View} from 'react-native'
import {SiftItem} from '@bsky.app/sift'
import {useLingui} from '@lingui/react/macro'

import {atoms as a, useTheme} from '#/alf'
import {Text} from '#/components/Typography'
import {type AutocompleteItemProps} from './types'

export function AutocompleteItemEmoji({
  active,
  props,
  item,
}: AutocompleteItemProps) {
  const t = useTheme()
  const {t: l} = useLingui()

  if (item.type !== 'emoji') return null

  return (
    <SiftItem
      {...props}
      testID={`emojiSuggestion-${item.key}`}
      accessibilityLabel={l`Insert ${item.emoji.name} emoji`}
      accessibilityHint=""
      style={s => [
        {paddingVertical: 6, paddingHorizontal: 10, minHeight: 48},
        a.flex_row,
        a.align_center,
        a.gap_sm,
        active || s.hovered || s.pressed ? [t.atoms.bg_contrast_25] : [],
      ]}>
      <Text emoji style={[a.text_xl, a.leading_tight]}>
        {item.value}
      </Text>
      <View style={[a.flex_1, a.gap_2xs]}>
        <Text numberOfLines={1} style={[a.text_sm, a.font_semi_bold]}>
          :{item.emoji.id}:
        </Text>
        <Text
          numberOfLines={1}
          style={[a.text_xs, t.atoms.text_contrast_medium]}>
          {item.emoji.name}
        </Text>
      </View>
    </SiftItem>
  )
}

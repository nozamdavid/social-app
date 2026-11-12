import {View} from 'react-native'
import {useLingui} from '@lingui/react/macro'
import {useQuery} from '@tanstack/react-query'

import {useHaptics} from '#/lib/haptics'
import {STALE} from '#/state/queries'
import {createQueryKey} from '#/state/queries/util'
import {atoms as a, useTheme} from '#/alf'
import {type AutocompleteEmoji} from '#/components/Autocomplete/types'
import {useEmojiSearch} from '#/components/Autocomplete/useAutocomplete/useEmojiSearch'
import {Button} from '#/components/Button'
import {Text} from '#/components/Typography'
import {type EmojiToken} from './tokens'

const useEmojiSuggestionsQuery = 'emoji-suggestions'

/** A compact, keyboard-preserving suggestion list for the native post editor. */
export function InlineSuggestions({
  token,
  onSelect,
}: {
  token: EmojiToken
  onSelect: (item: AutocompleteEmoji) => void
}) {
  const t = useTheme()
  const {t: l} = useLingui()
  const haptics = useHaptics()
  const search = useEmojiSearch()
  const {data: items} = useQuery({
    queryKey: createQueryKey(useEmojiSuggestionsQuery, {
      query: token.query,
      context: token.context,
    }),
    queryFn: () => search(token.query, 4, token.context),
    staleTime: STALE.INFINITY,
  })

  if (!items?.length) return null

  return (
    <View
      testID="emojiSuggestions"
      style={[
        a.mt_sm,
        a.border,
        a.rounded_sm,
        a.overflow_hidden,
        t.atoms.bg,
        t.atoms.border_contrast_low,
        {marginLeft: -62},
      ]}>
      {items.map(item => (
        <Button
          key={item.key}
          testID={`emojiSuggestion-${item.key}`}
          label={l`Insert ${item.emoji.name} emoji`}
          onPress={() => {
            haptics.selection()
            onSelect(item)
          }}
          hoverStyle={t.atoms.bg_contrast_25}
          style={[
            a.flex_row,
            a.align_center,
            a.gap_md,
            a.px_md,
            a.py_sm,
            {minHeight: 48},
          ]}>
          <Text emoji style={[a.text_2xl, a.leading_tight]}>
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
        </Button>
      ))}
    </View>
  )
}

import {View} from 'react-native'
import {plural} from '@lingui/core/macro'
import {Trans, useLingui} from '@lingui/react/macro'

import {atoms as a, useTheme} from '#/alf'
import {Text} from '#/components/Typography'
import {getThemeGroupOption, type ThemeGroup} from './groups'

/** Shared theme name, variant details and swatches for selecting and importing. */
export function ThemePreview({
  group,
  selectedId = '',
}: {
  group: ThemeGroup
  selectedId?: string
}) {
  const theme = getThemeGroupOption(group, selectedId)
  const active = group.variants.some(variant => variant.id === selectedId)
  const t = useTheme()
  const {t: l} = useLingui()
  const mode = theme.mode === 'light' ? l`Light` : l`Dark`
  const variant = theme.variantName
  const count = group.variants.length
  const details =
    !active && count > 1
      ? plural(count, {one: '# variant', other: '# variants'})
      : l`${variant} · ${mode}`
  return (
    <>
      <View style={[a.flex_1, a.gap_2xs]}>
        <Text emoji style={[a.text_md, a.font_semi_bold]}>
          {group.name}
        </Text>
        <Text emoji style={t.atoms.text_contrast_medium}>
          {details}
        </Text>
        {group.cachedVariantId === theme.id && (
          <Text style={t.atoms.text_contrast_medium}>
            <Trans>Saved on this device</Trans>
          </Text>
        )}
      </View>
      <View
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={[a.flex_row, a.gap_2xs]}>
        {[theme.colors.canvas, theme.colors.text, theme.colors.accent].map(
          (color, index) => (
            <View
              key={index}
              style={[
                a.rounded_full,
                a.border,
                t.atoms.border_contrast_low,
                {width: 16, height: 16, backgroundColor: color},
              ]}
            />
          ),
        )}
      </View>
    </>
  )
}

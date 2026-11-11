import {useState} from 'react'
import {
  Modal,
  Pressable,
  ScrollView,
  useWindowDimensions,
  View,
} from 'react-native'
import {Trans, useLingui} from '@lingui/react/macro'

import {ThemeProvider as LegacyThemeProvider} from '#/lib/ThemeContext'
import {atoms as a, ThemeProvider, useTheme} from '#/alf'
import {Button, ButtonIcon} from '#/components/Button'
import {TimesLarge_Stroke2_Corner0_Rounded as CloseIcon} from '#/components/icons/TimesLarge'
import {Text} from '#/components/Typography'
import {type ThemeOption} from './schema'
import {createWitchskyThemeOverrides} from './theme'
import {ThemeFeedPreview} from './ThemeFeedPreview'
import {ThemeVariantSelector} from './ThemeVariantSelector'

/** A local theme provider confines all variant changes to the sample feed. */
export function ThemePreviewModal({
  options,
  onClose,
}: {
  options: ThemeOption[]
  onClose: () => void
}) {
  const t = useTheme()
  const {t: l} = useLingui()
  const {height} = useWindowDimensions()
  const [selectedId, setSelectedId] = useState(options[0].id)
  const selected =
    options.find(option => option.id === selectedId) ?? options[0]
  return (
    <Modal
      transparent
      visible
      animationType="fade"
      onRequestClose={onClose}
      statusBarTranslucent
      navigationBarTranslucent
      supportedOrientations={[
        'portrait',
        'portrait-upside-down',
        'landscape',
        'landscape-left',
        'landscape-right',
      ]}>
      <View style={[a.flex_1, a.align_center, a.justify_center, a.p_lg]}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={l`Close theme preview`}
          accessibilityHint={undefined}
          onPress={onClose}
          style={[
            a.absolute,
            a.inset_0,
            {backgroundColor: 'rgba(0, 0, 0, 0.55)'},
          ]}
        />
        <View
          accessibilityViewIsModal
          testID="themePreviewModal"
          style={[
            a.w_full,
            a.rounded_lg,
            a.border,
            a.overflow_hidden,
            t.atoms.bg,
            t.atoms.border_contrast_low,
            {maxWidth: 520, maxHeight: '85%'},
          ]}>
          <View
            style={[
              a.flex_row,
              a.align_center,
              a.justify_between,
              a.gap_md,
              a.p_lg,
              a.border_b,
              t.atoms.border_contrast_low,
            ]}>
            <Text
              accessibilityRole="header"
              style={[a.flex_1, a.text_lg, a.font_bold]}>
              <Trans>Theme preview</Trans>
            </Text>
            <Button
              label={l`Close theme preview`}
              color="secondary"
              size="small"
              shape="round"
              onPress={onClose}
              testID="closeThemePreview">
              <ButtonIcon icon={CloseIcon} />
            </Button>
          </View>
          <ScrollView
            style={[a.flex_shrink]}
            contentContainerStyle={[a.p_lg, a.gap_md]}>
            <Text emoji style={[a.text_md, a.font_semi_bold]}>
              {selected.name}
            </Text>
            {options.length > 1 ? (
              <ThemeVariantSelector
                variants={options}
                value={selected.id}
                onChange={setSelectedId}
                label={l`Preview theme variant`}
                testIDPrefix="previewVariant-"
              />
            ) : (
              <Text emoji style={t.atoms.text_contrast_medium}>
                {selected.variantName}
              </Text>
            )}
            <View
              style={[
                a.rounded_md,
                a.border,
                a.overflow_hidden,
                t.atoms.border_contrast_low,
                {height: Math.min(400, Math.max(180, height * 0.42))},
              ]}
              testID="themePreviewFrame">
              <ThemeProvider
                theme={selected.mode}
                themesOverride={createWitchskyThemeOverrides(selected)}>
                <LegacyThemeProvider
                  theme={selected.mode}
                  colors={selected.colors}>
                  <ThemeFeedPreview />
                </LegacyThemeProvider>
              </ThemeProvider>
            </View>
            <Text style={t.atoms.text_contrast_medium}>
              <Trans>Sample content. Your current theme is unchanged.</Trans>
            </Text>
          </ScrollView>
        </View>
      </View>
    </Modal>
  )
}

import {View} from 'react-native'
import {plural} from '@lingui/core/macro'
import {Trans, useLingui} from '@lingui/react/macro'

import {APP_NAME} from '#/lib/app-brand'
import {useSession} from '#/state/session'
import * as SettingsList from '#/screens/Settings/components/SettingsList'
import {atoms as a, useTheme} from '#/alf'
import {Button, ButtonText} from '#/components/Button'
import * as Toggle from '#/components/forms/Toggle'
import {ColorPalette_Stroke2_Corner0_Rounded as PaletteIcon} from '#/components/icons/ColorPalette'
import {Loader} from '#/components/Loader'
import {Text} from '#/components/Typography'
import {AddThemesDialog} from '#/features/witchskyThemes/AddThemesDialog'
import {ApplyingThemeModal} from '#/features/witchskyThemes/ApplyingThemeModal'
import {
  getThemeGroupOption,
  groupThemes,
  type ThemeGroup,
} from '#/features/witchskyThemes/groups'
import {useWitchskyThemesQuery} from '#/features/witchskyThemes/queries'
import {ThemePreview} from '#/features/witchskyThemes/ThemePreview'
import {ThemeVariantSelector} from '#/features/witchskyThemes/ThemeVariantSelector'
import {useThemeApplication} from '#/features/witchskyThemes/useThemeApplication'

export function ThemesSection() {
  const t = useTheme()
  const {t: l} = useLingui()
  const {hasSession} = useSession()
  const application = useThemeApplication()
  const {selected} = application
  const query = useWitchskyThemesQuery()
  const loaded = query.data?.pages.flatMap(page => page.themes) ?? []
  const themes = groupThemes(loaded, selected)
  const activeGroup = themes.find(group =>
    group.variants.some(variant => variant.id === application.radioId),
  )
  const activeVariant = activeGroup
    ? getThemeGroupOption(activeGroup, application.radioId)
    : undefined
  const skipped = query.data?.pages.some(page => page.skipped > 0)

  function onChange(values: string[]) {
    if (values[0] === 'default') {
      application.requestTheme(null)
    } else {
      const group = themes.find(theme => theme.uri === values[0])
      if (group)
        application.requestTheme(
          getThemeGroupOption(group, application.radioId),
        )
    }
  }

  function onVariantChange(id: string) {
    /* SegmentedControl also reports the active item during initial layout. */
    if (application.applying || id === application.radioId) return
    const variant = activeGroup?.variants.find(option => option.id === id)
    if (variant) application.requestTheme(variant)
  }

  return (
    <SettingsList.Group iconInset={false} contentContainerStyle={[a.gap_md]}>
      <SettingsList.ItemIcon icon={PaletteIcon} />
      <SettingsList.ItemText>
        <Trans>Themes</Trans>
      </SettingsList.ItemText>
      <Text style={t.atoms.text_contrast_medium}>
        <Trans>
          Choose a Witchsky theme saved to your account. Themes include their
          own light or dark appearance.
        </Trans>
      </Text>
      <Toggle.Group
        type="radio"
        label={l`Themes`}
        values={[activeGroup?.uri ?? 'default']}
        disabled={application.applying}
        onChange={onChange}
        style={[a.gap_xs]}>
        <Toggle.Item
          name="default"
          label={l`Default theme`}
          testID="witchsky-theme-default"
          style={[
            a.flex_row,
            a.align_center,
            a.gap_md,
            a.py_sm,
            {minHeight: 56},
          ]}>
          <Toggle.Radio />
          <View style={[a.flex_1, a.gap_2xs]}>
            <Text style={[a.text_md, a.font_semi_bold]}>
              <Trans>Default</Trans>
            </Text>
            <Text style={t.atoms.text_contrast_medium}>
              <Trans>Use {APP_NAME}’s color mode settings.</Trans>
            </Text>
          </View>
        </Toggle.Item>
        {themes.map(theme => (
          <ThemeRow
            key={theme.uri}
            group={theme}
            selectedId={application.radioId}
          />
        ))}
      </Toggle.Group>
      {activeGroup && activeVariant && activeGroup.variants.length > 1 && (
        <View style={[a.w_full, a.gap_sm]} testID="witchsky-theme-variants">
          <Text style={[a.text_md, a.font_semi_bold]}>
            <Trans>Variant</Trans>
          </Text>
          <ThemeVariantSelector
            variants={activeGroup.variants}
            value={activeVariant.id}
            onChange={onVariantChange}
            label={l`Theme variant`}
            disabled={application.applying}
            testIDPrefix="witchsky-theme-variant-"
          />
        </View>
      )}
      <ApplyingThemeModal
        visible={application.applying}
        onShow={application.onModalShow}
      />
      {application.failed && (
        <Text
          style={t.atoms.text_contrast_medium}
          accessibilityLiveRegion="polite">
          <Trans>Couldn’t apply this theme. Please try again.</Trans>
        </Text>
      )}
      {!hasSession ? (
        <Text style={t.atoms.text_contrast_medium}>
          <Trans>Sign in to load your saved themes.</Trans>
        </Text>
      ) : query.isPending ? (
        <View style={[a.flex_row, a.align_center, a.gap_sm]}>
          <Loader size="sm" />
          <Text style={t.atoms.text_contrast_medium}>
            <Trans>Loading themes…</Trans>
          </Text>
        </View>
      ) : !query.isError && themes.length === 0 && !query.hasNextPage ? (
        <Text style={t.atoms.text_contrast_medium}>
          <Trans>No saved themes yet.</Trans>
        </Text>
      ) : null}
      {query.isError && (
        <Text
          style={t.atoms.text_contrast_medium}
          accessibilityLiveRegion="polite">
          <Trans>
            Couldn’t load your themes. Your current theme is still available.
            Try again.
          </Trans>
        </Text>
      )}
      {skipped && (
        <Text style={t.atoms.text_contrast_medium}>
          <Trans>Some saved themes couldn’t be loaded.</Trans>
        </Text>
      )}
      {hasSession && (
        <View style={[a.flex_row, a.flex_wrap, a.gap_sm]}>
          <AddThemesDialog />
          <Button
            label={l`Refresh themes`}
            color="secondary"
            size="small"
            style={{minHeight: 48}}
            disabled={query.isFetching}
            onPress={() => {
              void query.refetch()
            }}
            testID="witchsky-themes-refresh">
            <ButtonText>
              <Trans>Refresh themes</Trans>
            </ButtonText>
          </Button>
          {query.hasNextPage && (
            <Button
              label={l`Load more themes`}
              color="secondary"
              size="small"
              style={{minHeight: 48}}
              disabled={query.isFetching}
              onPress={() => {
                void query.fetchNextPage()
              }}>
              <ButtonText>
                <Trans>Load more</Trans>
              </ButtonText>
            </Button>
          )}
        </View>
      )}
    </SettingsList.Group>
  )
}

function ThemeRow({
  group,
  selectedId,
}: {
  group: ThemeGroup
  selectedId: string
}) {
  const theme = getThemeGroupOption(group, selectedId)
  const active = group.variants.some(variant => variant.id === selectedId)
  const {t: l} = useLingui()
  const mode = theme.mode === 'light' ? l`Light` : l`Dark`
  const name = group.name
  const variant = theme.variantName
  const count = group.variants.length
  const details =
    !active && count > 1
      ? plural(count, {one: '# variant', other: '# variants'})
      : l`${variant} · ${mode}`
  return (
    <Toggle.Item
      name={group.uri}
      label={l`${name}, ${details}`}
      testID={`witchsky-theme-${group.uri}`}
      style={[a.flex_row, a.align_center, a.gap_md, a.py_sm, {minHeight: 64}]}>
      <Toggle.Radio />
      <ThemePreview group={group} selectedId={selectedId} />
    </Toggle.Item>
  )
}

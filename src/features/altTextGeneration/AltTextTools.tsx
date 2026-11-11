import {View} from 'react-native'
import {Trans, useLingui} from '@lingui/react/macro'

import {atoms as a, useTheme} from '#/alf'
import {Button, ButtonText} from '#/components/Button'
import {Loader} from '#/components/Loader'
import {Text} from '#/components/Typography'
import {isAvailable} from '#/features/altTextGeneration/native'
import {useAltTextGeneration} from '#/features/altTextGeneration/useAltTextGeneration'
import {useDescriptionSupported} from '#/features/altTextGeneration/useDescriptionSupported'

export function AltTextTools({
  uri,
  hasAltText,
  onApply,
}: {
  uri: string
  hasAltText: boolean
  onApply: (text: string, append: boolean) => void
}) {
  const {t: l, i18n} = useLingui()
  const t = useTheme()
  const generation = useAltTextGeneration(uri)
  const descriptionSupported = useDescriptionSupported()

  const errors: Record<string, string> = {
    ERR_UNAVAILABLE: l`Image descriptions aren’t supported on this device. You can still use OCR or write ALT text yourself.`,
    ERR_BUSY: l`The on-device service is busy or finishing a previous request. Wait a moment and try again.`,
    ERR_STORAGE: l`There isn’t enough space for the description model. Free some storage and try again.`,
    ERR_SYSTEM_UPDATE: l`Image descriptions need an Android or AICore update. Update your device and try again.`,
    ERR_IMAGE: l`This image couldn’t be read. Try adding it again.`,
    ERR_BACKGROUND: l`Keep the app open while generating ALT text, then try again.`,
    ERR_TIMEOUT: l`The on-device service didn’t respond. Wait a moment and try again.`,
    ERR_NO_TEXT: l`No text was found. OCR reads Latin-script text; it doesn’t describe the image.`,
    ERR_EMPTY_DESCRIPTION: l`No description was returned. Try again or write ALT text yourself.`,
  }
  const progress = {
    idle: '',
    checking: l`Checking description support…`,
    recognizing: l`Reading image text…`,
    downloading:
      generation.downloadPercent === null
        ? l`Downloading description model…`
        : l`Downloading description model… ${i18n.number(generation.downloadPercent)}%`,
    describing: l`Describing image…`,
  }[generation.phase]

  return (
    <View style={[a.gap_sm]} testID="altTextTools">
      <View style={[a.flex_row, a.gap_sm]}>
        <Button
          testID="altTextOCR"
          label={l`Extract image text with OCR`}
          accessibilityHint={l`Reads Latin-script text from the image on your device`}
          size="small"
          color="secondary"
          disabled={!isAvailable || generation.busy}
          onPress={() => generation.start('ocr')}>
          <ButtonText>
            <Trans>OCR</Trans>
          </ButtonText>
        </Button>
        {descriptionSupported && (
          <Button
            testID="altTextDescribe"
            label={l`Describe image`}
            accessibilityHint={l`Suggests an English description on supported devices`}
            size="small"
            color="secondary"
            disabled={generation.busy}
            onPress={() => generation.start('describe')}>
            <ButtonText>
              <Trans>Describe</Trans>
            </ButtonText>
          </Button>
        )}
      </View>

      {!isAvailable && (
        <Text style={[a.text_sm, t.atoms.text_contrast_medium]}>
          <Trans>On-device ALT tools aren’t available in this app build.</Trans>
        </Text>
      )}

      {generation.busy && (
        <View style={[a.flex_row, a.align_center, a.gap_sm]}>
          <Loader size="sm" />
          <Text
            accessibilityLiveRegion="polite"
            style={[a.flex_1, a.text_sm, t.atoms.text_contrast_medium]}>
            {progress}
          </Text>
          <Button
            testID="altTextCancelGeneration"
            label={l`Cancel ALT text generation`}
            size="small"
            color="secondary"
            onPress={generation.cancel}>
            <ButtonText>
              <Trans>Cancel</Trans>
            </ButtonText>
          </Button>
        </View>
      )}

      {generation.downloadRequired && (
        <View style={[a.gap_sm]}>
          <Text style={[a.text_sm, t.atoms.text_contrast_medium]}>
            <Trans>
              Describe needs an on-device model download. Descriptions are
              generated in English. Your image stays on your device.
            </Trans>
          </Text>
          <Button
            testID="altTextDownloadModel"
            label={l`Download model and describe image`}
            size="small"
            color="secondary"
            onPress={() => generation.start('describe', true)}>
            <ButtonText>
              <Trans>Download and describe</Trans>
            </ButtonText>
          </Button>
        </View>
      )}

      {generation.error && (
        <Text
          accessibilityLiveRegion="polite"
          style={[a.text_sm, t.atoms.text_contrast_medium]}>
          {errors[generation.error] ??
            l`Couldn’t generate ALT text. Try again or write it yourself.`}
        </Text>
      )}

      {generation.suggestion && (
        <View style={[a.gap_sm, a.p_md, a.rounded_sm, t.atoms.bg_contrast_25]}>
          <Text style={[a.font_semi_bold]}>
            <Trans>Suggested ALT text</Trans>
          </Text>
          <Text emoji selectable testID="altTextSuggestion">
            {generation.suggestion.text}
          </Text>
          <Text style={[a.text_sm, t.atoms.text_contrast_medium]}>
            <Trans>Review the suggestion for accuracy before using it.</Trans>
          </Text>
          <View style={[a.flex_row, a.flex_wrap, a.gap_sm]}>
            <Button
              testID="altTextApplySuggestion"
              label={
                hasAltText ? l`Replace ALT text` : l`Use suggested ALT text`
              }
              size="small"
              color="secondary"
              onPress={() => {
                onApply(generation.suggestion!.text, false)
                generation.dismissSuggestion()
              }}>
              <ButtonText>
                {hasAltText ? (
                  <Trans>Replace ALT text</Trans>
                ) : (
                  <Trans>Use text</Trans>
                )}
              </ButtonText>
            </Button>
            {hasAltText && (
              <Button
                testID="altTextAppendSuggestion"
                label={l`Append suggestion to ALT text`}
                size="small"
                color="secondary"
                onPress={() => {
                  onApply(generation.suggestion!.text, true)
                  generation.dismissSuggestion()
                }}>
                <ButtonText>
                  <Trans>Append</Trans>
                </ButtonText>
              </Button>
            )}
            <Button
              label={l`Dismiss ALT text suggestion`}
              size="small"
              color="secondary"
              onPress={generation.dismissSuggestion}>
              <ButtonText>
                <Trans>Dismiss</Trans>
              </ButtonText>
            </Button>
          </View>
        </View>
      )}
    </View>
  )
}

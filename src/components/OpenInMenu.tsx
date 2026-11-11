import {Linking, View} from 'react-native'
import {AtUri} from '@atproto/syntax'
import {Trans, useLingui} from '@lingui/react/macro'

import {atoms as a} from '#/alf'
import {Button, ButtonIcon, ButtonText} from '#/components/Button'
import * as Dialog from '#/components/Dialog'
import {SquareArrowTopRight_Stroke2_Corner0_Rounded as ExternalLinkIcon} from '#/components/icons/Square'
import * as Toast from '#/components/Toast'
import {Text} from '#/components/Typography'
import {IS_WEB} from '#/env'

const destinations = [
  {id: 'pds', name: 'pds.ls', baseUrl: 'https://pds.ls/at/'},
  {
    id: 'blacksky',
    name: 'Blacksky',
    baseUrl: 'https://blacksky.community/profile/',
  },
  {id: 'witchsky', name: 'Witchsky', baseUrl: 'https://witchsky.app/profile/'},
  {
    id: 'red-dwarf',
    name: 'Red Dwarf',
    baseUrl: 'https://reddwarf.app/profile/',
  },
]

type Target = {type: 'profile'; did: string} | {type: 'post'; uri: string}

function destinationPaths(target: Target) {
  if (target.type === 'profile') {
    return {pds: target.did, social: target.did}
  }
  const uri = new AtUri(target.uri)
  return {
    pds: `${uri.host}/${uri.collection}/${uri.rkey}`,
    social: `${uri.host}/post/${uri.rkey}`,
  }
}

export function OpenInMenu({
  control,
  target,
}: {
  control: Dialog.DialogControlProps
  target: Target
}) {
  const {t: l} = useLingui()
  const paths = destinationPaths(target)

  function openDestination(url: string) {
    const open = () => {
      void Linking.openURL(url).catch(() => {
        Toast.show(l`Could not open this website. Please try again.`, {
          type: 'error',
        })
      })
    }

    if (IS_WEB) {
      // Preserve the browser's user gesture when opening a new tab.
      open()
      control.close()
    } else {
      control.close(open)
    }
  }

  return (
    <Dialog.Outer control={control} nativeOptions={{preventExpansion: true}}>
      <Dialog.Handle />
      <Dialog.ScrollableInner
        label={target.type === 'post' ? l`Open post in…` : l`Open profile in…`}>
        <View style={[a.gap_lg]}>
          <Text style={[a.text_2xl, a.font_semi_bold]}>
            <Trans>Open in…</Trans>
          </Text>
          <View style={[a.gap_sm]}>
            {destinations.map(destination => (
              <Button
                key={destination.id}
                testID={`${target.type}OpenIn-${destination.id}`}
                label={
                  target.type === 'post'
                    ? l`Open post in ${destination.name}`
                    : l`Open profile in ${destination.name}`
                }
                onPress={() =>
                  openDestination(
                    `${destination.baseUrl}${destination.id === 'pds' ? paths.pds : paths.social}`,
                  )
                }
                size="large"
                color="secondary"
                shape="rectangular">
                <ButtonText style={[a.flex_1, a.text_left]}>
                  {destination.name}
                </ButtonText>
                <ButtonIcon icon={ExternalLinkIcon} />
              </Button>
            ))}
          </View>
          <Button
            label={l`Cancel`}
            onPress={() => control.close()}
            size="large"
            color="secondary">
            <ButtonText>
              <Trans>Cancel</Trans>
            </ButtonText>
          </Button>
        </View>
        <Dialog.Close />
      </Dialog.ScrollableInner>
    </Dialog.Outer>
  )
}

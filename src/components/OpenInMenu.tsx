import {useState} from 'react'
import {Linking, View} from 'react-native'
import {AtUri} from '@atproto/syntax'
import {Trans, useLingui} from '@lingui/react/macro'

import {atoms as a} from '#/alf'
import {Button, ButtonIcon, ButtonText} from '#/components/Button'
import * as Dialog from '#/components/Dialog'
import {SquareArrowTopRight_Stroke2_Corner0_Rounded as ExternalLinkIcon} from '#/components/icons/Square'
import {Image, type ImageProps} from '#/components/Image'
import {Loader} from '#/components/Loader'
import * as Toast from '#/components/Toast'
import {Text} from '#/components/Typography'
import {IS_WEB} from '#/env'
import {type DiscoveredApp} from '#/features/atprotoApps/discovery'
import {useAtprotoAppsQuery} from '#/features/atprotoApps/queries'

type Target =
  {type: 'profile'; did: string; handle?: string} | {type: 'post'; uri: string}

const PDSLS_FAVICON: ImageProps['source'] = require('../../assets/icons/community/pdsls_favicon.png')

/**
 * Destinations the target can be opened in. Add new entries here.
 */
const destinations: {
  id: string
  name: string
  icon?: ImageProps['source']
  getUrl: (target: Target) => string
}[] = [
  {
    id: 'pds',
    name: 'pds.ls',
    icon: PDSLS_FAVICON,
    getUrl: target => {
      if (target.type === 'profile') {
        return `https://pds.ls/at/${target.did}`
      }
      const uri = new AtUri(target.uri)
      return `https://pds.ls/at/${uri.host}/${uri.collection}/${uri.rkey}`
    },
  },
]

export function OpenInMenu({
  control,
  target,
}: {
  control: Dialog.DialogControlProps
  target: Target
}) {
  const {t: l} = useLingui()
  const [isOpen, setIsOpen] = useState(false)
  // Only profiles get discovery, and only once the dialog is actually open.
  const apps = useAtprotoAppsQuery({
    did: target.type === 'profile' ? target.did : '',
    handle: target.type === 'profile' ? target.handle : undefined,
    enabled: target.type === 'profile' && isOpen,
  })

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
    <Dialog.Outer
      control={control}
      onOpen={() => setIsOpen(true)}
      onClose={() => setIsOpen(false)}
      nativeOptions={{preventExpansion: true}}>
      <Dialog.Handle />
      <Dialog.ScrollableInner
        label={target.type === 'post' ? l`Open post in…` : l`Open profile in…`}>
        <View style={[a.gap_lg]}>
          <Text style={[a.text_2xl, a.font_semi_bold]}>
            <Trans>Open in…</Trans>
          </Text>
          <View style={[a.gap_sm]}>
            {destinations.map(destination => (
              <DestinationButton
                key={destination.id}
                testID={`${target.type}OpenIn-${destination.id}`}
                label={
                  target.type === 'post'
                    ? l`Open post in ${destination.name}`
                    : l`Open profile in ${destination.name}`
                }
                name={destination.name}
                icon={destination.icon}
                onPress={() => openDestination(destination.getUrl(target))}
              />
            ))}
          </View>
          {target.type === 'profile' && apps.isLoading ? (
            <View style={[a.align_center, a.py_sm]}>
              <Loader size="md" />
            </View>
          ) : null}
          {apps.data?.length ? (
            <View style={[a.gap_sm]}>
              <Text style={[a.text_md, a.font_semi_bold]}>
                <Trans>Apps</Trans>
              </Text>
              {apps.data.map(app => (
                <DestinationButton
                  key={app.id}
                  testID={`profileOpenIn-${app.id}`}
                  label={l`Open profile in ${app.name}`}
                  name={app.name}
                  icon={app.icon}
                  onPress={() => openDestination(app.url)}
                />
              ))}
            </View>
          ) : null}
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

function DestinationButton({
  testID,
  label,
  name,
  icon,
  onPress,
}: {
  testID: string
  label: string
  name: string
  icon?: DiscoveredApp['icon']
  onPress: () => void
}) {
  return (
    <Button
      testID={testID}
      label={label}
      onPress={onPress}
      size="large"
      color="secondary"
      shape="rectangular"
      style={[a.px_md, a.gap_sm]}>
      {icon ? (
        <Image
          source={icon}
          style={[a.rounded_xs, {width: 20, height: 20}]}
          accessibilityIgnoresInvertColors
        />
      ) : null}
      <ButtonText emoji numberOfLines={1} style={[a.flex_1, a.text_left]}>
        {name}
      </ButtonText>
      <ButtonIcon icon={ExternalLinkIcon} />
    </Button>
  )
}

import {useEffect, useState} from 'react'
import {useIsMutating, useQueryClient} from '@tanstack/react-query'

import {
  APPVIEW_PRESETS,
  DEFAULT_INFRASTRUCTURE,
  IMAGE_CDN_PRESETS,
  normalizeAppview,
  normalizeImageCdn,
  normalizePlcDirectory,
  PLC_DIRECTORY_PRESETS,
} from '#/features/infrastructure/config'
import {
  getInfrastructureSettings,
  saveInfrastructureSettings,
  updateInfrastructureSettings,
  useInfrastructureSettings,
} from '#/features/infrastructure/preferences'

/** Retain custom drafts only across the next AppView cache remount. */
let pendingCustomDrafts:
  | {
      accountDid: string | undefined
      screenKey: string
      appviewHostname: string
      cdn: {choice: string; hostname: string}
      plc: {choice: string; hostname: string}
    }
  | undefined

/** Immediate settings, with isolated drafts for custom hostname inputs. */
export function useInfrastructureControls({
  beforeAppviewChange,
  accountDid,
  screenKey,
}: {
  screenKey: string
  accountDid?: string
  beforeAppviewChange: () => void
}) {
  const settings = useInfrastructureSettings()
  const queryClient = useQueryClient()
  const appviewBusy = useIsMutating() > 0
  const [appviewChoice, setAppviewChoice] = useState<string>(
    APPVIEW_PRESETS.some(p => p.hostname === settings.appviewHostname)
      ? settings.appviewHostname
      : 'custom',
  )
  const [customAppview, setCustomAppviewValue] = useState(
    settings.appviewHostname,
  )
  const [cdnDraft, setCdnDraft] = useState(() => {
    if (
      pendingCustomDrafts?.screenKey === screenKey &&
      pendingCustomDrafts?.accountDid === accountDid &&
      pendingCustomDrafts?.appviewHostname === settings.appviewHostname
    ) {
      return {
        ...pendingCustomDrafts.cdn,
      }
    }
    return {
      choice: IMAGE_CDN_PRESETS.some(
        p => p.hostname === settings.imageCdnHostname,
      )
        ? settings.imageCdnHostname
        : 'custom',
      hostname: settings.imageCdnHostname,
    }
  })
  const [plcDraft, setPlcDraft] = useState(() => {
    if (
      pendingCustomDrafts?.screenKey === screenKey &&
      pendingCustomDrafts?.accountDid === accountDid &&
      pendingCustomDrafts?.appviewHostname === settings.appviewHostname
    ) {
      return {...pendingCustomDrafts.plc}
    }
    return {
      choice: PLC_DIRECTORY_PRESETS.some(
        p => p.hostname === settings.plcDirectoryHostname,
      )
        ? settings.plcDirectoryHostname
        : 'custom',
      hostname: settings.plcDirectoryHostname,
    }
  })
  const {choice: plcDirectoryChoice, hostname: plcDirectory} = plcDraft
  const {choice: imageCdnChoice, hostname: imageCdn} = cdnDraft
  useEffect(() => {
    if (
      pendingCustomDrafts?.accountDid !== accountDid ||
      pendingCustomDrafts?.screenKey === screenKey
    ) {
      pendingCustomDrafts = undefined
    }
  }, [accountDid, screenKey])
  const [appviewInvalid, setAppviewInvalid] = useState(false)
  const [cdnInvalid, setCdnInvalid] = useState(false)
  const [plcDirectoryInvalid, setPlcDirectoryInvalid] = useState(false)

  function commitAppview(hostname: string) {
    if (hostname !== getInfrastructureSettings().appviewHostname) {
      if (queryClient.isMutating() > 0) return false
      beforeAppviewChange()
      pendingCustomDrafts = {
        accountDid,
        screenKey,
        appviewHostname: hostname,
        cdn: cdnDraft,
        plc: plcDraft,
      }
      updateInfrastructureSettings({appviewHostname: hostname})
    }
    return true
  }

  function chooseAppview(choice: string) {
    if (
      choice !== 'custom' &&
      choice !== getInfrastructureSettings().appviewHostname &&
      queryClient.isMutating() > 0
    )
      return
    setAppviewChoice(choice)
    setAppviewInvalid(false)
    if (choice !== 'custom') commitAppview(choice)
  }

  function chooseImageCdn(choice: string) {
    setCdnDraft(current => ({...current, choice}))
    setCdnInvalid(false)
    if (choice !== 'custom')
      updateInfrastructureSettings({imageCdnHostname: choice})
  }

  function setCustomAppview(value: string) {
    setCustomAppviewValue(value)
    setAppviewInvalid(false)
  }
  function setImageCdn(value: string) {
    setCdnDraft(current => ({...current, hostname: value}))
    setCdnInvalid(false)
  }

  function applyAppview() {
    let hostname: string
    try {
      if (!customAppview.trim()) throw new Error('Missing hostname')
      hostname = normalizeAppview(customAppview)
    } catch {
      setAppviewInvalid(true)
      return false
    }
    if (
      hostname !== getInfrastructureSettings().appviewHostname &&
      queryClient.isMutating() > 0
    )
      return false
    setCustomAppviewValue(hostname)
    setAppviewChoice(
      APPVIEW_PRESETS.some(p => p.hostname === hostname) ? hostname : 'custom',
    )
    setAppviewInvalid(false)
    return commitAppview(hostname)
  }

  function applyImageCdn() {
    let hostname: string
    try {
      if (!imageCdn.trim()) throw new Error('Missing hostname')
      hostname = normalizeImageCdn(imageCdn)
    } catch {
      setCdnInvalid(true)
      return false
    }
    setCdnDraft({
      hostname,
      choice: IMAGE_CDN_PRESETS.some(p => p.hostname === hostname)
        ? hostname
        : 'custom',
    })
    setCdnInvalid(false)
    updateInfrastructureSettings({imageCdnHostname: hostname})
    return true
  }

  function choosePlcDirectory(choice: string) {
    setPlcDraft(current => ({...current, choice}))
    setPlcDirectoryInvalid(false)
    if (choice !== 'custom')
      updateInfrastructureSettings({plcDirectoryHostname: choice})
  }

  function setPlcDirectory(hostname: string) {
    setPlcDraft(current => ({...current, hostname}))
    setPlcDirectoryInvalid(false)
  }

  function applyPlcDirectory() {
    let hostname: string
    try {
      if (!plcDirectory.trim()) throw new Error('Missing hostname')
      hostname = normalizePlcDirectory(plcDirectory)
    } catch {
      setPlcDirectoryInvalid(true)
      return false
    }
    setPlcDraft({
      hostname,
      choice: PLC_DIRECTORY_PRESETS.some(p => p.hostname === hostname)
        ? hostname
        : 'custom',
    })
    setPlcDirectoryInvalid(false)
    updateInfrastructureSettings({plcDirectoryHostname: hostname})
    return true
  }

  function resetDefaults() {
    const switching = getInfrastructureSettings().appviewHostname !== ''
    if (switching && queryClient.isMutating() > 0) return
    setAppviewChoice('')
    setCustomAppviewValue('')
    setCdnDraft({choice: '', hostname: ''})
    setPlcDraft({choice: '', hostname: ''})
    setAppviewInvalid(false)
    setCdnInvalid(false)
    setPlcDirectoryInvalid(false)
    if (switching) beforeAppviewChange()
    saveInfrastructureSettings(DEFAULT_INFRASTRUCTURE)
  }

  return {
    settings,
    plcDirectoryChoice,
    plcDirectory,
    plcDirectoryInvalid,
    plcDirectoryDirty: plcDirectory !== settings.plcDirectoryHostname,
    choosePlcDirectory,
    setPlcDirectory,
    applyPlcDirectory,
    appviewChoice,
    customAppview,
    imageCdnChoice,
    imageCdn,
    appviewInvalid,
    cdnInvalid,
    appviewBusy,
    appviewDirty: customAppview !== settings.appviewHostname,
    cdnDirty: imageCdn !== settings.imageCdnHostname,
    resetBlocked: appviewBusy && settings.appviewHostname !== '',
    chooseAppview,
    chooseImageCdn,
    setCustomAppview,
    setImageCdn,
    applyAppview,
    applyImageCdn,
    resetDefaults,
    setUseLinkRedirects(value: boolean) {
      updateInfrastructureSettings({useLinkRedirects: value})
    },
  }
}

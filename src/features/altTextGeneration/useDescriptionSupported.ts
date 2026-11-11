import {useEffect, useState} from 'react'
import {AppState} from 'react-native'

import * as native from '#/features/altTextGeneration/native'

export function useDescriptionSupported() {
  const [supported, setSupported] = useState(false)

  useEffect(() => {
    if (!native.isAvailable) return
    let mounted = true
    let latestCheck = 0

    async function check() {
      const id = ++latestCheck
      try {
        const status = await native.getDescriptionStatus()
        if (mounted && id === latestCheck) {
          setSupported(
            status === 'available' ||
              status === 'downloadable' ||
              status === 'downloading',
          )
        }
      } catch {
        if (mounted && id === latestCheck) setSupported(false)
      }
    }

    void check()
    const subscription = AppState.addEventListener('change', state => {
      // AICore can become ready after initialization or a system update.
      if (state === 'active') void check()
    })
    return () => {
      mounted = false
      subscription.remove()
    }
  }, [])

  return native.isAvailable && supported
}

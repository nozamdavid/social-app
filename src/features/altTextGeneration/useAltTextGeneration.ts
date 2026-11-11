import {useEffect, useRef, useState} from 'react'

import * as native from '#/features/altTextGeneration/native'
import {
  type GenerationMethod,
  type GenerationPhase,
} from '#/features/altTextGeneration/types'

let nextRequestId = 0

export function useAltTextGeneration(uri: string) {
  const active = useRef<string | null>(null)
  const [phase, setPhase] = useState<GenerationPhase>('idle')
  const [downloadRequired, setDownloadRequired] = useState(false)
  const [downloadPercent, setDownloadPercent] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [suggestion, setSuggestion] = useState<{
    method: GenerationMethod
    text: string
  } | null>(null)

  useEffect(() => {
    const listener = native.onProgress(event => {
      if (event.requestId !== active.current) return
      setPhase(event.phase)
      setDownloadPercent(
        event.totalBytes > 0
          ? Math.min(
              100,
              Math.max(
                0,
                Math.round((event.downloadedBytes / event.totalBytes) * 100),
              ),
            )
          : null,
      )
    })
    return () => {
      listener?.remove()
      if (active.current) native.cancel(active.current)
      active.current = null
    }
  }, [uri])

  async function start(method: GenerationMethod, allowDownload = false) {
    if (active.current) return
    const id = `alt-${++nextRequestId}`
    active.current = id
    setError(null)
    setSuggestion(null)
    setDownloadRequired(false)
    setDownloadPercent(null)
    setPhase(method === 'ocr' ? 'recognizing' : 'checking')
    try {
      if (method === 'describe') {
        const status = await native.getDescriptionStatus()
        if (active.current !== id) return
        if (status === 'unavailable') {
          setError('ERR_UNAVAILABLE')
          return
        }
        if (status !== 'available' && !allowDownload) {
          setDownloadRequired(true)
          return
        }
        setPhase(status === 'available' ? 'describing' : 'downloading')
      }
      const text = await native.generate(id, method, uri, allowDownload)
      if (active.current !== id) return
      if (text.trim()) {
        setSuggestion({method, text: text.trim()})
      } else {
        setError(method === 'ocr' ? 'ERR_NO_TEXT' : 'ERR_EMPTY_DESCRIPTION')
      }
    } catch (err) {
      if (active.current !== id) return
      const code =
        err && typeof err === 'object' && 'code' in err
          ? String(err.code)
          : 'ERR_GENERATION'
      if (code === 'ERR_MODEL_REQUIRED') setDownloadRequired(true)
      else if (code !== 'ERR_CANCELLED') setError(code)
    } finally {
      if (active.current === id) {
        active.current = null
        setPhase('idle')
      }
    }
  }

  function cancel() {
    if (active.current) native.cancel(active.current)
    active.current = null
    setPhase('idle')
    setDownloadRequired(false)
  }

  return {
    phase,
    busy: phase !== 'idle',
    downloadRequired,
    downloadPercent,
    error,
    suggestion,
    start,
    cancel,
    dismissSuggestion: () => setSuggestion(null),
  }
}

import {requireOptionalNativeModule} from 'expo'

import {
  type DescriptionStatus,
  type GenerationMethod,
  type GenerationProgress,
} from '#/features/altTextGeneration/types'

type AltTextModule = {
  addListener(
    event: 'onProgress',
    listener: (progress: GenerationProgress) => void,
  ): {remove(): void}
  getDescriptionStatus(): Promise<DescriptionStatus>
  generate(
    requestId: string,
    method: GenerationMethod,
    uri: string,
    allowDownload: boolean,
  ): Promise<string>
  cancel(requestId: string): Promise<void>
}

const native = requireOptionalNativeModule<AltTextModule>('CaelumAltText')

export const isAvailable = !!native

export async function getDescriptionStatus(): Promise<DescriptionStatus> {
  return native ? native.getDescriptionStatus() : 'unavailable'
}

export async function generate(
  requestId: string,
  method: GenerationMethod,
  uri: string,
  allowDownload: boolean,
): Promise<string> {
  if (!native) throw new Error('On-device ALT tools are unavailable')
  return native.generate(requestId, method, uri, allowDownload)
}

export function cancel(requestId: string) {
  void native?.cancel(requestId).catch(() => {})
}

export function onProgress(listener: (event: GenerationProgress) => void) {
  return native?.addListener('onProgress', listener)
}

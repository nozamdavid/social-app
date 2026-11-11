import {
  type DescriptionStatus,
  type GenerationMethod,
  type GenerationProgress,
} from '#/features/altTextGeneration/types'

export const isAvailable = false

export function getDescriptionStatus(): Promise<DescriptionStatus> {
  return Promise.resolve('unavailable')
}

export function generate(
  _requestId: string,
  _method: GenerationMethod,
  _uri: string,
  _allowDownload: boolean,
): Promise<string> {
  return Promise.reject(new Error('On-device ALT tools are unavailable'))
}

export function cancel(_requestId: string) {}

export function onProgress(
  _listener: (event: GenerationProgress) => void,
): {remove(): void} | undefined {
  return undefined
}

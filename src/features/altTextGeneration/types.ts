export type GenerationMethod = 'ocr' | 'describe'

export type DescriptionStatus =
  'available' | 'downloadable' | 'downloading' | 'unavailable'

export type GenerationPhase =
  'idle' | 'checking' | 'recognizing' | 'downloading' | 'describing'

export type GenerationProgress = {
  requestId: string
  phase: Exclude<GenerationPhase, 'idle'>
  downloadedBytes: number
  totalBytes: number
}

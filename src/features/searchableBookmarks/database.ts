import {MMKV} from 'react-native-mmkv'

const database = new MMKV({id: 'searchable-bookmarks-v1'})

export function readSnapshot(key: string): Promise<Uint8Array | undefined> {
  const value = database.getBuffer(key)
  return Promise.resolve(value ? new Uint8Array(value) : undefined)
}

export function writeSnapshot(key: string, value: Uint8Array) {
  database.set(key, value.slice().buffer)
  return Promise.resolve()
}

export function deleteSnapshot(key: string) {
  database.delete(key)
  return Promise.resolve()
}

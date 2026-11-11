let database: Promise<IDBDatabase> | undefined

function open() {
  return (database ??= new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open('searchable-bookmarks', 1)
    request.onupgradeneeded = () =>
      request.result.createObjectStore('snapshots')
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => {
      database = undefined
      reject(request.error ?? new Error('Could not open local saved posts'))
    }
  }))
}

async function transaction<T>(
  mode: IDBTransactionMode,
  operation: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  const db = await open()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('snapshots', mode)
    const request = operation(tx.objectStore('snapshots'))
    tx.oncomplete = () => resolve(request.result)
    tx.onerror = tx.onabort = () =>
      reject(
        tx.error ||
          request.error ||
          new Error('Local saved posts transaction failed'),
      )
  })
}

export function readSnapshot(key: string): Promise<Uint8Array | undefined> {
  return transaction('readonly', store => store.get(key))
}

export async function writeSnapshot(key: string, value: Uint8Array) {
  await transaction('readwrite', store => store.put(value, key))
}

export async function deleteSnapshot(key: string) {
  await transaction('readwrite', store => store.delete(key))
}

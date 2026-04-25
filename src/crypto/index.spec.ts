import { generateUserKeys, encryptBlock, decryptBlock, signData, verifySignature } from './index.js'

describe('crypto', () => {
  it('should generate user keys', async () => {
    const keys = await generateUserKeys()
    expect(keys.userPublicKey).toBeInstanceOf(Uint8Array)
    expect(keys.userPublicKey.length).toBe(32)
    expect(keys.userPrivateKey).toBeInstanceOf(Uint8Array)
    expect(keys.userPrivateKey?.length).toBe(64) // Ed25519 private key is typically 64 bytes in libp2p (seed + pubkey)
    expect(keys.storageKey.length).toBe(32)
    expect(keys.linkKey?.length).toBe(32)
    expect(keys.dataKey?.length).toBe(32)
  })

  it('should encrypt and decrypt a block', async () => {
    const key = new Uint8Array(32).fill(1)
    const plaintext = new TextEncoder().encode('hello world')
    const ciphertext = encryptBlock(key, plaintext)
    
    expect(ciphertext).not.toEqual(plaintext)
    expect(ciphertext.length).toBe(12 + 16 + plaintext.length)

    const decrypted = decryptBlock(key, ciphertext)
    expect(new TextDecoder().decode(decrypted)).toBe('hello world')
  })

  it('should fail to decrypt with wrong key', async () => {
    const key1 = new Uint8Array(32).fill(1)
    const key2 = new Uint8Array(32).fill(2)
    const plaintext = new TextEncoder().encode('hello world')
    const ciphertext = encryptBlock(key1, plaintext)
    
    expect(() => decryptBlock(key2, ciphertext)).toThrow()
  })

  it('should sign and verify data', async () => {
    const keys = await generateUserKeys()
    const data = new TextEncoder().encode('some data to sign')
    
    const signature = await signData(keys.userPrivateKey!, data)
    expect(signature).toBeInstanceOf(Uint8Array)
    
    const isValid = await verifySignature(keys.userPublicKey, data, signature)
    expect(isValid).toBe(true)

    const isInvalid = await verifySignature(keys.userPublicKey, new TextEncoder().encode('tampered data'), signature)
    expect(isInvalid).toBe(false)
  })
})

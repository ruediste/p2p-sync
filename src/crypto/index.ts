import crypto from 'node:crypto'
import { generateKeyPair, privateKeyFromRaw, publicKeyFromRaw } from '@libp2p/crypto/keys'
import type { UserKeys } from '../gen/user_pb.js'
import { create } from '@bufbuild/protobuf'
import { UserKeysSchema } from '../gen/user_pb.js'
import { concat as uint8ArrayConcat } from 'uint8arrays/concat'

const ALGORITHM = 'aes-256-gcm'
const IV_LENGTH = 12
const TAG_LENGTH = 16

/**
 * Creates a new Ed25519 keypair and three AES-256-GCM symmetric keys (storage, link, data).
 * Returns a UserKeys protobuf object.
 */
export async function generateUserKeys(): Promise<UserKeys> {
  const privateKey = await generateKeyPair('Ed25519')
  const storageKey = crypto.randomBytes(32)
  const linkKey = crypto.randomBytes(32)
  const dataKey = crypto.randomBytes(32)

  return create(UserKeysSchema, {
    userPublicKey: privateKey.publicKey.raw,
    userPrivateKey: privateKey.raw,
    storageKey,
    linkKey,
    dataKey
  })
}

/**
 * AES-GCM encryption with random IV. Used by all three layers with their respective keys.
 * Prepends IV and Auth Tag to the ciphertext.
 */
export function encryptBlock(key: Uint8Array, plaintext: Uint8Array): Uint8Array {
  const iv = crypto.randomBytes(IV_LENGTH)
  const cipher = crypto.createCipheriv(ALGORITHM, key, iv)
  const ciphertext = uint8ArrayConcat([cipher.update(plaintext), cipher.final()])
  const tag = cipher.getAuthTag()
  // Format: [IV (12 bytes)][Tag (16 bytes)][Ciphertext (variable)]
  return uint8ArrayConcat([iv, tag, ciphertext])
}

/**
 * Corresponding decryption for encryptBlock.
 */
export function decryptBlock(key: Uint8Array, ciphertextWithIvAndTag: Uint8Array): Uint8Array {
  if (ciphertextWithIvAndTag.length < IV_LENGTH + TAG_LENGTH) {
    throw new Error('Invalid ciphertext: too short')
  }
  const iv = ciphertextWithIvAndTag.subarray(0, IV_LENGTH)
  const tag = ciphertextWithIvAndTag.subarray(IV_LENGTH, IV_LENGTH + TAG_LENGTH)
  const ciphertext = ciphertextWithIvAndTag.subarray(IV_LENGTH + TAG_LENGTH)
  
  const decipher = crypto.createDecipheriv(ALGORITHM, key, iv)
  decipher.setAuthTag(tag)
  return uint8ArrayConcat([decipher.update(ciphertext), decipher.final()])
}

/**
 * Ed25519 signature over data.
 */
export async function signData(privateKeyRaw: Uint8Array, data: Uint8Array): Promise<Uint8Array> {
  const privateKey = await privateKeyFromRaw(privateKeyRaw)
  return privateKey.sign(data)
}

/**
 * Verifies an Ed25519 signature.
 */
export async function verifySignature(publicKeyRaw: Uint8Array, data: Uint8Array, signature: Uint8Array): Promise<boolean> {
  const publicKey = publicKeyFromRaw(publicKeyRaw)
  return publicKey.verify(data, signature)
}

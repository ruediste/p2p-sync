import { create, toBinary } from "@bufbuild/protobuf";
import { StorageBlockSchema } from "../gen/storage_pb.js";
import { sha256 } from 'multiformats/hashes/sha2'
import { CID } from 'multiformats/cid'
import * as RAW from 'multiformats/codecs/raw'

export class StorageBlock {
  constructor(public readonly data: Uint8Array, public readonly cid: CID) {}

  static async create(data: Uint8Array): Promise<StorageBlock> {
    const hash = await sha256.digest(data)
    const cid = CID.create(1, RAW.code, hash)
    return new StorageBlock(data, cid)
  }

  toBinary(): Uint8Array {
    const message = create(StorageBlockSchema, {
      cid: this.cid.bytes,
      data: this.data
    });
    return toBinary(StorageBlockSchema, message);
  }
}

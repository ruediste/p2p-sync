import { HaveUsersSchema, WantUsersSchema } from "@/gen/p2p-sync-protocol_pb";
import { create, toBinary } from "@bufbuild/protobuf";
import { AnySchema, anyPack } from "@bufbuild/protobuf/wkt";
import type { Stream } from "@libp2p/interface";

export class SyncMessageHandler {
  constructor(private stream: Stream) {}

  handleMessage(msg: any) {
    if (msg.type === WantUsersSchema) {
      // Logic from the original file (currently incomplete in original)
      const response = create(HaveUsersSchema, {
        userIds: [], // To be implemented with actual user IDs from UserController
      });
      const anyResponse = anyPack(HaveUsersSchema, response);
      this.stream.send(toBinary(AnySchema, anyResponse));
    }
    // Add other message handlers as needed
  }

  async sendWantUsers() {
    const msg = create(WantUsersSchema, {});
    const anyMsg = anyPack(WantUsersSchema, msg);
    await this.stream.send(toBinary(AnySchema, anyMsg));
  }
}

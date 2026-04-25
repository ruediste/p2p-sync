import {
  create,
  fromBinary,
  isMessage,
  toBinary,
  type DescMessage,
  type MessageShape,
} from "@bufbuild/protobuf";
import { AnySchema, anyPack, anyUnpack } from "@bufbuild/protobuf/wkt";
import type { Stream } from "@libp2p/interface";
import type { Components } from "../components.js";
import {
  HaveUserDatasSchema,
  HaveUsersSchema,
  WantUserDatasSchema,
  WantUsersSchema,
} from "../gen/p2p-sync-protocol_pb.js";
import { StorageUserDataSchema } from "../gen/storage_pb.js";
import { p2pSyncRegistry } from "../registry.js";
import { StorageUserData } from "../storage/StorageUserData.js";

export class SyncMessageHandler {
  constructor(
    private stream: Stream,
    private components: Pick<
      Components,
      "userController" | "replicationController"
    >,
  ) {}

  async handleMessage(data: Uint8Array) {
    const anyMsg = fromBinary(AnySchema, data);
    const message = anyUnpack(anyMsg, p2pSyncRegistry);

    if (isMessage(message, WantUsersSchema)) {
      const users = this.components.userController.getUsers();
      const response = create(HaveUsersSchema, {
        userIds: users.map((u: any) => u.publicKey),
      });
      await this.send(HaveUsersSchema, response);
    }

    if (isMessage(message, WantUserDatasSchema)) {
      const userDatas: Uint8Array[] = [];
      for (const userId of message.userIds) {
        const roots =
          await this.components.replicationController.getUserDataRoots(userId);
        for (const root of roots) {
          userDatas.push(root.toBinary());
        }
      }
      const response = create(HaveUserDatasSchema, {
        userDatas: userDatas.map((d) => fromBinary(StorageUserDataSchema, d)),
      });
      await this.send(HaveUserDatasSchema, response);
    }

    if (isMessage(message, HaveUserDatasSchema)) {
      for (const proto of message.userDatas) {
        const userData = StorageUserData.fromProto(proto);
        await this.components.replicationController.handleIncomingUserData(
          userData,
        );
      }
    }
  }

  async sendWantUsers() {
    const msg = create(WantUsersSchema, {});
    await this.send(WantUsersSchema, msg);
  }

  async sendWantUserDatas(userIds: Uint8Array[]) {
    const msg = create(WantUserDatasSchema, { userIds });
    await this.send(WantUserDatasSchema, msg);
  }

  private async send<Desc extends DescMessage>(
    schema: Desc,
    message: MessageShape<Desc>,
  ) {
    const anyMsg = anyPack(schema, message);
    await this.stream.send(toBinary(AnySchema, anyMsg));
  }
}

import { createRegistry } from "@bufbuild/protobuf";
import { file_clock } from "./gen/clock_pb.js";
import { file_node } from "./gen/node_pb.js";
import { file_p2p_sync_protocol } from "./gen/p2p-sync-protocol_pb.js";
import { file_storage } from "./gen/storage_pb.js";
import { file_user } from "./gen/user_pb.js";

export const p2pSyncRegistry = createRegistry(
  file_clock,
  file_node,
  file_p2p_sync_protocol,
  file_storage,
  file_user,
);

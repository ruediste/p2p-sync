import { createRegistry } from "@bufbuild/protobuf";
import { file_p2p_sync_protocol } from "./gen/p2p-sync-protocol_pb";

export const syncProtocolId = "/p2p-sync/v1/";

export const syncRegistry = createRegistry(file_p2p_sync_protocol);

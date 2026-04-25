import { UserKeysSchema } from "@/gen/user_pb.js";
import { create, toBinary } from "@bufbuild/protobuf";
import { beforeEach, describe, expect, it, jest } from "@jest/globals";

// Mock must be hoisted or defined before the module that uses it is imported
jest.unstable_mockModule("node:fs/promises", () => ({
  default: {
    readFile: jest.fn(),
    writeFile: jest.fn(),
  },
}));

// We need to dynamically import the module we want to test AFTER mocking
const { loadOrCreateUserKeys: loadOrCreateKeys } =
  await import("@/user/UserKeysManagement.js");
const fs = (await import("node:fs/promises")).default as any;

describe("userKeysManagement", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("should create new keys if the file does not exist", async () => {
    const error = new Error("File not found");
    (error as any).code = "ENOENT";
    fs.readFile.mockRejectedValue(error);
    fs.writeFile.mockResolvedValue(undefined);

    const keys = await loadOrCreateKeys();

    expect(keys).toBeDefined();
    expect(keys.storageKey).toBeInstanceOf(Uint8Array);
    expect(keys.linkKey).toBeInstanceOf(Uint8Array);
    expect(keys.dataKey).toBeInstanceOf(Uint8Array);
    expect(keys.userPrivateKey).toBeInstanceOf(Uint8Array);
    expect(keys.userPublicKey).toBeInstanceOf(Uint8Array);

    expect(fs.writeFile).toHaveBeenCalledWith(
      "userKeys.proto",
      expect.any(Uint8Array),
    );
  });

  it("should load existing keys if the file exists", async () => {
    const mockKeys = create(UserKeysSchema, {
      storageKey: new Uint8Array([1, 2, 3]),
      linkKey: new Uint8Array([4, 5, 6]),
      dataKey: new Uint8Array([7, 8, 9]),
      userPrivateKey: new Uint8Array([10]),
      userPublicKey: new Uint8Array([11]),
    });
    const binaryData = toBinary(UserKeysSchema, mockKeys);
    fs.readFile.mockResolvedValue(Buffer.from(binaryData));

    const keys = await loadOrCreateKeys();

    expect(keys.storageKey).toEqual(mockKeys.storageKey);
    expect(keys.linkKey).toEqual(mockKeys.linkKey);
    expect(keys.dataKey).toEqual(mockKeys.dataKey);
    expect(fs.writeFile).not.toHaveBeenCalled();
  });

  it("should throw an error if readFile fails with a different error", async () => {
    const error = new Error("Permission denied");
    (error as any).code = "EACCES";
    fs.readFile.mockRejectedValue(error);

    await expect(loadOrCreateKeys()).rejects.toThrow("Permission denied");
  });
});

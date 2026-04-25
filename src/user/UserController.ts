import type { Component, Components } from "@/components";

export interface UserInfo {
  publicKey: Uint8Array;
}

export class UserController implements Component {
  private users: UserInfo[] = [];

  constructor(c: Pick<Components, "dataStore">) {}

  initialize() {
    // TODO: Load from dataStore
  }

  getUsers(): UserInfo[] {
    return this.users;
  }

  addUser(publicKey: Uint8Array) {
    this.users.push({ publicKey });
  }
}

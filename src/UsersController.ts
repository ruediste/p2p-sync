import type { Component, Components } from "./components";

export class UserController implements Component {
  constructor(c: Pick<Components, "dataStore">) {}

  initialize() {}
}

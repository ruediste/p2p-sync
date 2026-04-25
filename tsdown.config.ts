import { defineConfig } from "tsdown";

export default defineConfig({
  exports: true,
  alias: {
    "@": "./src",
    // "@shared": "../shared",
  },
});

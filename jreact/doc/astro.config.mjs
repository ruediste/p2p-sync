import mdx from "@astrojs/mdx";
import starlight from "@astrojs/starlight";
import { defineConfig } from "astro/config";
import path from "node:path";
import snippetValidator from "./src/plugins/vite-snippet-validator.js";

export default defineConfig({
  integrations: [
    starlight({
      title: "JReact",
      description:
        "A Java 21+ declarative UI framework backed by Swing and Java2D.",
      sidebar: [
        {
          label: "Guides",
          items: [{ autogenerate: { directory: "guides" } }],
        },
        {
          label: "Reference",
          items: [{ autogenerate: { directory: "reference" } }],
        },
      ],
      customCss: ["./src/styles/custom.css"],
    }),
    mdx(),
  ],
  vite: {
    plugins: [snippetValidator()],
    resolve: {
      alias: {
        "@examples": path.resolve(
          "../jreact-storybook/src/main/java/com/github/ruediste/jreact/storybook",
        ),
        "@components": path.resolve("./src/components"),
      },
    },
  },
});

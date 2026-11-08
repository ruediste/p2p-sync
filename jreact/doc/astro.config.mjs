import mdx from "@astrojs/mdx";
import starlight from "@astrojs/starlight";
import mermaid from "astro-mermaid";
import { defineConfig } from "astro/config";
import { satteri } from "@astrojs/markdown-satteri";
import path from "node:path";
import snippetValidator from "./src/plugins/vite-snippet-validator.js";

export default defineConfig({
  markdown: {
    processor: satteri({ features: { rawHtml: true } }),
  },
  integrations: [
    starlight({
      title: "JReact",
      logo: { src: "./src/assets/logo.svg" },
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
    mermaid({
      theme: "forest",
      autoTheme: true,
    }),
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

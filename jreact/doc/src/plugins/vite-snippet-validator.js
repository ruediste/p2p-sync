import fs from "node:fs";
import path from "node:path";

export default function snippetValidator() {
  const DOCS_DIR = path.resolve("src");
  const EXAMPLES_DIR = path.resolve(
    "../jreact-storybook/src/main/java/com/github/ruediste/jreact/storybook",
  );

  function validate() {
    console.log("Validating snippets...");
    if (!fs.existsSync(DOCS_DIR) || !fs.existsSync(EXAMPLES_DIR)) return;

    let hasErrors = false;

    function getDocFiles(dir, fileList = []) {
      const files = fs.readdirSync(dir);
      for (const file of files) {
        const filePath = path.join(dir, file);
        if (fs.statSync(filePath).isDirectory()) {
          getDocFiles(filePath, fileList);
        } else if (file.endsWith(".md") || file.endsWith(".mdx")) {
          fileList.push(filePath);
        }
      }
      return fileList;
    }

    const patterns = [
      /<CodeSnippet[^>]*file=["']([^"']+)["'][^>]*tag=["']([^"']+)["']/g,
      /getJavaSnippet\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]+)['"]\s*\)/g,
    ];

    const docFiles = getDocFiles(DOCS_DIR);

    for (const docFile of docFiles) {
      const content = fs.readFileSync(docFile, "utf8");
      for (const regex of patterns) {
        regex.lastIndex = 0;
        let match;
        while ((match = regex.exec(content)) !== null) {
          const [, relPath, tag] = match;
          const javaFile = path.join(EXAMPLES_DIR, relPath);

          if (!fs.existsSync(javaFile)) {
            console.error(
              `\n❌ [Snippet Error] Java file not found: ${relPath} (referenced in ${path.relative(process.cwd(), docFile)})`,
            );
            hasErrors = true;
            continue;
          }

          const javaContent = fs.readFileSync(javaFile, "utf8");
          if (!javaContent.includes(`// --8<-- [start:${tag}]`)) {
            console.error(
              `\n❌ [Snippet Error] Tag '${tag}' missing in ${relPath} (referenced in ${path.relative(process.cwd(), docFile)})`,
            );
            hasErrors = true;
          }
        }
      }
    }

    if (hasErrors) {
      console.warn(
        "\x1b[31m%s\x1b[0m",
        "\n⚠️  Documentation contains broken snippet references! Check errors above.\n",
      );
    }
  }

  return {
    name: "vite-plugin-snippet-validator",
    buildStart() {
      validate();
    },
    handleHotUpdate({ file, server }) {
      console.log(`Hot update detected for file: ${file}`);
      if (file.endsWith(".md") || file.endsWith(".mdx")) {
        validate();
      }
    },
  };
}

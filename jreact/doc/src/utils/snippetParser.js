import fs from "node:fs";
import path from "node:path";

const fileCache = new Map();

export function getJavaSnippet(relativePath, tag) {
  const absolutePath = path.resolve(
    "../../jreact-storybook/src/main/java/com/github/ruediste/jreact/storybook/",
    relativePath,
  );

  if (!fs.existsSync(absolutePath)) {
    return `// Error: File not found at ${absolutePath}`;
  }

  let fileContent;
  if (fileCache.has(absolutePath)) {
    fileContent = fileCache.get(absolutePath);
  } else {
    fileContent = fs.readFileSync(absolutePath, "utf8");
    fileCache.set(absolutePath, fileContent);
  }

  const startMarker = `// --8<-- [start:${tag}]`;
  const endMarker = `// --8<-- [end:${tag}]`;
  const lines = fileContent.split("\n");

  const startIndex = lines.findIndex((line) => line.includes(startMarker));
  const endIndex = lines.findIndex((line) => line.includes(endMarker));

  if (startIndex === -1 || endIndex === -1) {
    return `// Error: Snippet tag '${tag}' not found in ${relativePath}`;
  }

  const snippetLines = lines.slice(startIndex + 1, endIndex);

  const firstLine = snippetLines.find((l) => l.trim().length > 0);
  const indentMatch = firstLine ? firstLine.match(/^(\s*)/) : null;
  const indentLength = indentMatch ? indentMatch[1].length : 0;

  return snippetLines.map((line) => line.slice(indentLength)).join("\n");
}

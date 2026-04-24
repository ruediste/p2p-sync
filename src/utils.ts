import dotenv from "dotenv";
import express from "express";
import reload from "reload";

dotenv.config({ path: ".env", quiet: true });

process.on("SIGTERM", () => {
  console.info("SIGTERM signal received. Shutting down ...");
  process.exit(0);
});

const app = express();

// Add JSON body parser middleware for POST requests
app.use(express.json());

export { app };

export async function startServer() {
  const isProd = process.env.APP_STAGE === "prod";

  if (isProd) {
    console.log("Serving static files from public/");
    app.use(express.static("public"));
    app.get(/.*/, (_req, res) => {
      res.sendFile("index.html", { root: "public/" });
    });
  } else {
    reload(app);
  }

  const port = process.env.APP_PORT || (isProd ? 8080 : 5735);
  app.listen(port, () => {
    console.log(`Server is running on port ${port}`);
  });
}

export function sleep(ms: number) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

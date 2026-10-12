import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import { createServer } from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const siteRoot = path.resolve(repoRoot, process.env.SITE_ROOT ?? "docs");
const host = process.env.HOST ?? "127.0.0.1";
const requestedPort = Number(process.env.PORT ?? readArg("--port") ?? 4173);
const maxPortAttempts = 20;

const contentTypes = {
  ".css": "text/css; charset=utf-8",
  ".gif": "image/gif",
  ".html": "text/html; charset=utf-8",
  ".ico": "image/x-icon",
  ".jpeg": "image/jpeg",
  ".jpg": "image/jpeg",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".md": "text/markdown; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".png": "image/png",
  ".svg": "image/svg+xml; charset=utf-8",
  ".txt": "text/plain; charset=utf-8",
  ".webp": "image/webp"
};

function readArg(name) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : undefined;
}

async function findFile(requestUrl) {
  const url = new URL(requestUrl, `http://${host}:${requestedPort}`);
  const decodedPath = decodeURIComponent(url.pathname);
  let candidate = path.resolve(siteRoot, `.${decodedPath}`);
  const relative = path.relative(siteRoot, candidate);

  if (relative.startsWith("..") || path.isAbsolute(relative)) {
    return { status: 403 };
  }

  try {
    const candidateStat = await stat(candidate);
    if (candidateStat.isDirectory()) {
      candidate = path.join(candidate, "index.html");
    }
    await stat(candidate);
    return { filePath: candidate, status: 200 };
  } catch {
    return { filePath: path.join(siteRoot, "404.html"), status: 404 };
  }
}

async function handleRequest(request, response) {
  try {
    const result = await findFile(request.url ?? "/");
    if (result.status === 403 || !result.filePath) {
      response.writeHead(403, { "Content-Type": "text/plain; charset=utf-8" });
      response.end("Forbidden");
      return;
    }

    const ext = path.extname(result.filePath).toLowerCase();
    response.writeHead(result.status, {
      "Cache-Control": "no-store",
      "Content-Type": contentTypes[ext] ?? "application/octet-stream"
    });

    if (request.method === "HEAD") {
      response.end();
      return;
    }

    createReadStream(result.filePath).pipe(response);
  } catch (error) {
    response.writeHead(500, { "Content-Type": "text/plain; charset=utf-8" });
    response.end(`Internal server error\n${error instanceof Error ? error.message : error}`);
  }
}

function startServer(port) {
  return new Promise((resolve, reject) => {
    const server = createServer(handleRequest);
    server.once("error", reject);
    server.listen(port, host, () => resolve(server));
  });
}

let server;
let port = requestedPort;
for (; port < requestedPort + maxPortAttempts; port += 1) {
  try {
    server = await startServer(port);
    break;
  } catch (error) {
    if (error?.code !== "EADDRINUSE") throw error;
  }
}

if (!server) {
  throw new Error(`No open port found from ${requestedPort} to ${requestedPort + maxPortAttempts - 1}`);
}

console.log(`Serving ${siteRoot}`);
console.log(`Local: http://${host}:${port}/`);

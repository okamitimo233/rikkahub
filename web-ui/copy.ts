import {
  copyFileSync,
  mkdirSync,
  readdirSync,
  rmSync,
  statSync,
} from "node:fs";
import { join, dirname } from "node:path";

const SOURCE_DIR = "./build/client";
const TARGET_DIR = "../web/src/main/resources/static";

function removeRecursiveIfExists(path: string) {
  try {
    rmSync(path, { recursive: true, force: true });
  } catch {
    // ignore
  }
}

function copyDirectory(src: string, dest: string) {
  mkdirSync(dest, { recursive: true });

  const entries = readdirSync(src, { withFileTypes: true });

  for (const entry of entries) {
    const srcPath = join(src, entry.name);
    const destPath = join(dest, entry.name);

    if (entry.isDirectory()) {
      copyDirectory(srcPath, destPath);
    } else {
      mkdirSync(dirname(destPath), { recursive: true });
      copyFileSync(srcPath, destPath);
    }
  }
}

function pruneRemoved(src: string, dest: string) {
  const srcEntries = new Set(readdirSync(src, { withFileTypes: true }).map((e) => e.name));
  const destEntries = readdirSync(dest, { withFileTypes: true });

  for (const entry of destEntries) {
    const destPath = join(dest, entry.name);
    if (!srcEntries.has(entry.name)) {
      removeRecursiveIfExists(destPath);
    } else if (entry.isDirectory()) {
      pruneRemoved(join(src, entry.name), destPath);
    }
  }
}

try {
  console.log("📦 Starting build output copy...");
  console.log(`   Source: ${SOURCE_DIR}`);
  console.log(`   Target: ${TARGET_DIR}`);

  try {
    statSync(SOURCE_DIR);
  } catch {
    console.error(`❌ Source directory not found: ${SOURCE_DIR}`);
    console.error("   Please run build first.");
    process.exit(1);
  }

  mkdirSync(TARGET_DIR, { recursive: true });
  pruneRemoved(SOURCE_DIR, TARGET_DIR);
  copyDirectory(SOURCE_DIR, TARGET_DIR);

  console.log("✅ Build output copied successfully!");
} catch (error) {
  console.error("❌ Copy failed:", error);
  process.exit(1);
}

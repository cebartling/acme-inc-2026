import { chromium, type Browser, type BrowserContextOptions, type Page } from 'playwright';
import { existsSync } from 'node:fs';
import { mkdir, readdir, rename, stat } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { config } from './config.ts';

const here = dirname(fileURLToPath(import.meta.url));

type Mode = 'live' | 'record';
type PlainScenario = (page: Page) => Promise<void>;
type ScreenplayScenario = (browser: Browser, contextOptions: BrowserContextOptions) => Promise<void>;
type LoadedScenario =
  | { isScreenplay: false; fn: PlainScenario }
  | { isScreenplay: true; fn: ScreenplayScenario };

function parseArgs(argv: string[]): { name: string; mode: Mode } {
  const positional = argv.filter((a) => !a.startsWith('--'));
  const name = positional[0];
  if (!name) {
    throw new Error('Usage: bun run src/run.ts <demo-name> [--mode=live|record]');
  }
  const modeArg = argv.find((a) => a.startsWith('--mode='))?.split('=')[1];
  if (modeArg !== undefined && modeArg !== 'live' && modeArg !== 'record') {
    throw new Error(`Invalid --mode value \`${modeArg}\`. Expected 'live' or 'record'.`);
  }
  const mode: Mode = modeArg === 'record' ? 'record' : 'live';
  return { name, mode };
}

async function preflight(): Promise<void> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), config.preflightTimeoutMs);
  try {
    await fetch(config.customerAppUrl, { signal: controller.signal });
  } catch (err) {
    throw new Error(
      `Customer app not reachable at ${config.customerAppUrl}. ` +
        `Start services with \`just demo-up\` and try again. ` +
        `(underlying error: ${(err as Error).message})`
    );
  } finally {
    clearTimeout(timer);
  }
}

async function loadScenario(name: string): Promise<LoadedScenario> {
  const screenplayPath = join(here, 'screenplay', 'scenarios', `${name}.ts`);
  if (existsSync(screenplayPath)) {
    const mod = await import(`./screenplay/scenarios/${name}.ts`);
    if (typeof mod.default !== 'function') {
      throw new Error(`Screenplay scenario \`${name}\` does not export a default function.`);
    }
    return { isScreenplay: true, fn: mod.default as ScreenplayScenario };
  }

  const plainPath = join(here, 'scenarios', `${name}.ts`);
  if (!existsSync(plainPath)) {
    throw new Error(
      `Unknown demo \`${name}\`. Looked in demos/src/screenplay/scenarios/ and demos/src/scenarios/.`
    );
  }
  const mod = await import(`./scenarios/${name}.ts`);
  if (typeof mod.default !== 'function') {
    throw new Error(`Scenario \`${name}\` does not export a default function.`);
  }
  return { isScreenplay: false, fn: mod.default as PlainScenario };
}

async function timestampedRename(
  sourcePath: string,
  dir: string,
  name: string
): Promise<string> {
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const target = join(dir, `${name}-${stamp}.webm`);
  await rename(sourcePath, target);
  return target;
}

async function findNewestWebm(dir: string): Promise<string | null> {
  const entries = await readdir(dir);
  const webms = entries.filter((e) => e.endsWith('.webm'));
  if (webms.length === 0) return null;

  let newest: string | null = null;
  let newestMtime = 0;
  for (const file of webms) {
    const filePath = join(dir, file);
    const s = await stat(filePath);
    if (s.mtimeMs > newestMtime) {
      newestMtime = s.mtimeMs;
      newest = filePath;
    }
  }
  return newest;
}

async function main(): Promise<void> {
  const { name, mode } = parseArgs(Bun.argv.slice(2));

  await preflight();
  const loaded = await loadScenario(name);

  const modeLabel = loaded.isScreenplay ? `${mode}/screenplay` : mode;
  console.log(`▶ Running demo \`${name}\` in ${modeLabel} mode...`);

  const browser = await chromium.launch({
    headless: false,
    slowMo: config.slowMoMs,
  });

  await mkdir(config.recordingsDir, { recursive: true });

  const contextOptions: BrowserContextOptions = {
    viewport: config.viewport,
  };
  if (mode === 'record') {
    contextOptions.recordVideo = {
      dir: config.recordingsDir,
      size: config.viewport,
    };
  }

  if (loaded.isScreenplay) {
    try {
      await loaded.fn(browser, contextOptions);
      if (mode === 'live') {
        await new Promise((resolve) => setTimeout(resolve, 2000));
      }
    } finally {
      await browser.close().catch(() => {});
    }

    if (mode === 'record') {
      const sourcePath = await findNewestWebm(config.recordingsDir);
      if (sourcePath) {
        const out = await timestampedRename(sourcePath, config.recordingsDir, name);
        console.log(`✓ Recording saved: ${out}`);
      } else {
        console.warn(`⚠ Recording requested but no video path was produced.`);
      }
    } else {
      console.log('✓ Demo complete.');
    }
  } else {
    const context = await browser.newContext(contextOptions);
    const page = await context.newPage();
    const video = mode === 'record' ? page.video() : null;

    try {
      await loaded.fn(page);
      if (mode === 'live') {
        await page.waitForTimeout(2000);
      }
    } finally {
      await page.close().catch(() => {});
      await context.close().catch(() => {});
      await browser.close().catch(() => {});
    }

    if (mode === 'record') {
      const sourcePath = await video?.path();
      if (sourcePath) {
        const out = await timestampedRename(sourcePath, config.recordingsDir, name);
        console.log(`✓ Recording saved: ${out}`);
      } else {
        console.warn(`⚠ Recording requested but no video path was produced.`);
      }
    } else {
      console.log('✓ Demo complete.');
    }
  }
}

main().catch((err) => {
  console.error(`✗ ${err.message}`);
  process.exit(1);
});

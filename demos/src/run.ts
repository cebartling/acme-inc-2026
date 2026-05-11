import { chromium, type Page } from 'playwright';
import { mkdir, rename } from 'node:fs/promises';
import { join } from 'node:path';
import { config } from './config.ts';

type Mode = 'live' | 'record';
type Scenario = (page: Page) => Promise<void>;

function parseArgs(argv: string[]): { name: string; mode: Mode } {
  const positional = argv.filter((a) => !a.startsWith('--'));
  const name = positional[0];
  if (!name) {
    throw new Error('Usage: bun run src/run.ts <demo-name> [--mode=live|record]');
  }
  const modeArg = argv.find((a) => a.startsWith('--mode='))?.split('=')[1];
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

async function loadScenario(name: string): Promise<Scenario> {
  try {
    const mod = await import(`./scenarios/${name}.ts`);
    if (typeof mod.default !== 'function') {
      throw new Error(`Scenario \`${name}\` does not export a default function.`);
    }
    return mod.default as Scenario;
  } catch (err) {
    if ((err as NodeJS.ErrnoException).code === 'ERR_MODULE_NOT_FOUND') {
      throw new Error(`Unknown demo \`${name}\`. Looked in demos/src/scenarios/.`);
    }
    throw err;
  }
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

async function main(): Promise<void> {
  const { name, mode } = parseArgs(Bun.argv.slice(2));

  await preflight();
  const scenario = await loadScenario(name);

  console.log(`▶ Running demo \`${name}\` in ${mode} mode...`);

  const browser = await chromium.launch({
    headless: false,
    slowMo: config.slowMoMs,
  });

  await mkdir(config.recordingsDir, { recursive: true });

  const contextOptions: Parameters<typeof browser.newContext>[0] = {
    viewport: config.viewport,
  };
  if (mode === 'record') {
    contextOptions.recordVideo = {
      dir: config.recordingsDir,
      size: config.viewport,
    };
  }

  const context = await browser.newContext(contextOptions);
  const page = await context.newPage();
  // Capture the video handle before page.close() — page.video() is null afterwards.
  const video = mode === 'record' ? page.video() : null;

  try {
    await scenario(page);
    if (mode === 'live') {
      await page.waitForTimeout(2000);
    }
  } finally {
    await page.close();
    // context.close() flushes the recorded video to disk.
    await context.close();
    await browser.close();
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

main().catch((err) => {
  console.error(`✗ ${err.message}`);
  process.exit(1);
});

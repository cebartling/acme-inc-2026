import { existsSync } from 'node:fs';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const stateFile = resolve(here, '..', '.last-registered.json');

export interface RegisteredAccount {
  email: string;
  password: string;
  registeredAt: string;
}

export async function saveRegisteredAccount(account: Omit<RegisteredAccount, 'registeredAt'>): Promise<void> {
  await mkdir(dirname(stateFile), { recursive: true });
  const payload: RegisteredAccount = { ...account, registeredAt: new Date().toISOString() };
  await writeFile(stateFile, JSON.stringify(payload, null, 2) + '\n', 'utf8');
}

export async function loadRegisteredAccount(): Promise<RegisteredAccount | null> {
  if (!existsSync(stateFile)) return null;
  try {
    const raw = await readFile(stateFile, 'utf8');
    return JSON.parse(raw) as RegisteredAccount;
  } catch {
    return null;
  }
}

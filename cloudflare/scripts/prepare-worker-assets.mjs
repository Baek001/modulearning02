import { existsSync, rmSync } from 'node:fs';
import { resolve } from 'node:path';

const redirectsPath = resolve(process.cwd(), '../frontend/dist/_redirects');

if (existsSync(redirectsPath)) {
  rmSync(redirectsPath, { force: true });
  console.log(`Removed Pages-only redirect file for Worker deploy: ${redirectsPath}`);
} else {
  console.log(`No _redirects file found at ${redirectsPath}`);
}

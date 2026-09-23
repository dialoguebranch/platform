import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { beforeAll, describe, expect, it } from 'vitest';

const packageRoot = fileURLToPath(new URL('.', import.meta.url));
const manifest = JSON.parse(readFileSync(new URL('package.json', import.meta.url), 'utf8'));
let packedFiles;

beforeAll(() => {
    // Resolved by name rather than via `process.env.npm_execpath` (only populated when this
    // process was itself spawned by an npm script) so the spec also works run directly, e.g.
    // `npx vitest run packaging.spec.js` or from an IDE test runner.
    const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm';
    const output = execFileSync(npmCommand, [
        'pack', '--dry-run', '--json', '--ignore-scripts',
    ], { cwd: packageRoot, encoding: 'utf8' });
    packedFiles = JSON.parse(output)[0].files.map(file => file.path);
}, 30000);

describe('npm package contents', () => {
    it('ships the runtime modules, including the shared client base', () => {
        expect(packedFiles).toEqual(expect.arrayContaining([
            'BaseClient.js', 'ClientState.js', 'DialogueBranchClient.js',
            'DialogueBranchAuthoringClient.js', 'DialogueBranchError.js', 'protocol.js',
            'model/Action.js', 'model/AutoForwardReply.js', 'model/BasicReply.js',
            'model/DialogueStep.js', 'model/OngoingDialogue.js', 'model/Reply.js',
            'model/Segment.js', 'model/ServerInfo.js', 'model/Statement.js',
            'model/User.js', 'model/Variable.js',
            'util/AbstractLogger.js', 'util/ConsoleLogger.js', 'package.json',
        ]));
    });

    it('does not ship specs, test configuration or the development lockfile', () => {
        expect(packedFiles.filter(path => path.endsWith('.spec.js'))).toEqual([]);
        expect(packedFiles).not.toContain('vitest.config.js');
        expect(packedFiles).not.toContain('package-lock.json');
    });

    it('ships a package-local copy of the project licence, without the root LICENSE\'s ' +
        'vendored-third-party-code footer (this package has no vendored code of its own)', () => {
        expect(packedFiles).toContain('LICENSE');
        const rootLicenseText = readFileSync(new URL('../../LICENSE', import.meta.url), 'utf8');
        const [mitText] = rootLicenseText.split('\n\n---\n\n');
        expect(readFileSync(new URL('LICENSE', import.meta.url), 'utf8')).toBe(mitText + '\n');
    });

    it('has its version synced with the monorepo-wide global.json (via `npm run sync-version`)', () => {
        const globalJson = JSON.parse(readFileSync(new URL('../../global.json', import.meta.url), 'utf8'));
        expect(manifest.version).toBe(globalJson.version);
    });

    it('points consumers back to the monorepo and issue tracker', () => {
        expect(manifest.repository).toEqual({
            type: 'git',
            url: 'git+https://github.com/dialoguebranch/platform.git',
            directory: 'packages/client-js',
        });
        expect(manifest.homepage).toBe('https://github.com/dialoguebranch/platform/tree/main/packages/client-js#readme');
        expect(manifest.bugs).toEqual({ url: 'https://github.com/dialoguebranch/platform/issues' });
    });
});

import { describe, it, expect } from 'vitest';
import { Statement } from './Statement.js';
import { BasicReply } from './BasicReply.js';
import { AutoForwardReply } from './AutoForwardReply.js';
import { ServerInfo } from './ServerInfo.js';
import { SupportedVariable } from './SupportedVariable.js';
import { User } from './User.js';
import { OngoingDialogue } from './OngoingDialogue.js';
import { Variable } from './Variable.js';
import { VariableHistoryEntry } from './VariableHistoryEntry.js';

describe('Statement.fromJSON', () => {
    it('parses its segments', () => {
        const statement = Statement.fromJSON({
            segments: [{ segmentType: 'TEXT', text: 'hi' }],
        });
        expect(statement.segments).toHaveLength(1);
        expect(statement.fullStatement()).toBe('hi');
    });

    it('defaults to no segments when absent', () => {
        expect(Statement.fromJSON({}).segments).toEqual([]);
    });
});

describe('BasicReply.fromJSON / AutoForwardReply.fromJSON', () => {
    it('BasicReply carries its statement and actions', () => {
        const reply = BasicReply.fromJSON({
            replyId: 1,
            endsDialogue: false,
            statement: { segments: [{ segmentType: 'TEXT', text: 'yes' }] },
            actions: [{ type: 'generic', value: 'PING', parameters: {} }],
        });
        expect(reply.replyId).toBe(1);
        expect(reply.statement.fullStatement()).toBe('yes');
        expect(reply.actions).toHaveLength(1);
    });

    it('AutoForwardReply has no statement', () => {
        const reply = AutoForwardReply.fromJSON({ replyId: 2, endsDialogue: true, actions: [] });
        expect(reply.replyId).toBe(2);
        expect(reply.endsDialogue).toBe(true);
        expect(reply.actions).toEqual([]);
    });
});

describe('ServerInfo.fromJSON', () => {
    it('carries the four /info/all fields', () => {
        const info = ServerInfo.fromJSON({
            serviceVersion: '0.1.8', protocolVersion: '1', build: '2026-09-17T00:00:00Z', upTime: '2d 4h 15m',
        });
        expect(info.serviceVersion).toBe('0.1.8');
        expect(info.protocolVersion).toBe('1');
        expect(info.build).toBe('2026-09-17T00:00:00Z');
        expect(info.upTime).toBe('2d 4h 15m');
    });
});

describe('User.fromJSON', () => {
    it('maps the BFF /whoami "username" field to name', () => {
        const user = User.fromJSON({ username: 'alice', roles: ['editor'] });
        expect(user.name).toBe('alice');
        expect(user.roles).toEqual(['editor']);
    });

    it('defaults roles to an empty array when absent', () => {
        expect(User.fromJSON({ username: 'bob' }).roles).toEqual([]);
    });
});

describe('OngoingDialogue.fromJSON', () => {
    it('carries dialogueName, loggedDialogueId and secondsSinceLastEngagement', () => {
        const ongoing = OngoingDialogue.fromJSON({
            dialogueName: 'menu', loggedDialogueId: 'ld-1', secondsSinceLastEngagement: 42,
        });
        expect(ongoing.dialogueName).toBe('menu');
        expect(ongoing.loggedDialogueId).toBe('ld-1');
        expect(ongoing.secondsSinceLastEngagement).toBe(42);
    });
});

describe('Variable.fromJSON', () => {
    it('carries name, value, updatedTime, updatedTimeZone and updatedSource', () => {
        const variable = Variable.fromJSON({
            name: 'gold', value: '10', updatedTime: 1700000000000,
            updatedTimeZone: 'Europe/Lisbon', updatedSource: 'DLB_SCRIPT',
        });
        expect(variable.name).toBe('gold');
        expect(variable.value).toBe('10');
        expect(variable.updatedTimeZone).toBe('Europe/Lisbon');
        expect(variable.updatedSource).toBe('DLB_SCRIPT');
    });
});

describe('VariableHistoryEntry.fromJSON', () => {
    it('carries id, name, value, recordedTime, recordedTimeZone and source', () => {
        const entry = VariableHistoryEntry.fromJSON({
            id: 7, name: 'mood', value: 'fine', recordedTime: 1700000000000,
            recordedTimeZone: 'Europe/Lisbon', source: 'INPUT_REPLY',
        });
        expect(entry.id).toBe(7);
        expect(entry.name).toBe('mood');
        expect(entry.value).toBe('fine');
        expect(entry.isRemoval).toBe(false);
        expect(entry.recordedTime).toBe(1700000000000);
        expect(entry.recordedTimeZone).toBe('Europe/Lisbon');
        expect(entry.source).toBe('INPUT_REPLY');
    });

    it('treats a null or missing value as a removal', () => {
        expect(VariableHistoryEntry.fromJSON({ id: 1, name: 'a', value: null }).isRemoval).toBe(true);
        expect(VariableHistoryEntry.fromJSON({ id: 2, name: 'a' }).value).toBeNull();
    });

    it('keeps falsy-but-real values (0, false, empty string) as values, not removals', () => {
        for (const value of [0, false, '']) {
            const entry = VariableHistoryEntry.fromJSON({ id: 3, name: 'a', value });
            expect(entry.value).toBe(value);
            expect(entry.isRemoval).toBe(false);
        }
    });
});

describe('SupportedVariable.fromJSON', () => {
    it('carries the name, description and the hints the service gave', () => {
        const variable = SupportedVariable.fromJSON({
            name: 'settings.theme', description: 'The colour theme',
            type: 'string', values: ['light', 'dark'], readOnly: false,
        });
        expect(variable.name).toBe('settings.theme');
        expect(variable.description).toBe('The colour theme');
        expect(variable.type).toBe('string');
        expect(variable.values).toEqual(['light', 'dark']);
        expect(variable.readOnly).toBe(false);
    });

    it('leaves a hint the service didn\'t give as null, never a default', () => {
        const variable = SupportedVariable.fromJSON({ name: 'mood' });
        expect(variable.description).toBeNull();
        expect(variable.type).toBeNull();
        expect(variable.values).toBeNull();
        expect(variable.readOnly).toBeNull();
    });
});

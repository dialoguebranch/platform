import { describe, it, expect } from 'vitest';
import { Action } from './Action.js';
import { Segment } from './Segment.js';
import { Statement } from './Statement.js';
import { BasicReply } from './BasicReply.js';
import { AutoForwardReply } from './AutoForwardReply.js';
import { DialogueBranchClient } from '../DialogueBranchClient.js';

describe('Action.fromJSON', () => {
    it('carries type, value and parameters', () => {
        const action = Action.fromJSON({
            type: 'link',
            value: 'https://www.dialoguebranch.com/',
            parameters: { text: 'website' },
        });
        expect(action.type).toBe('link');
        expect(action.value).toBe('https://www.dialoguebranch.com/');
        expect(action.parameters).toEqual({ text: 'website' });
        expect(action.parameter('text')).toBe('website');
        expect(action.parameter('missing')).toBeNull();
    });

    it('defaults parameters to an empty object when absent', () => {
        const action = Action.fromJSON({ type: 'image', value: 'dog.png' });
        expect(action.parameters).toEqual({});
    });
});

describe('Segment.fromJSON', () => {
    it('builds a TEXT segment', () => {
        const segment = Segment.fromJSON({ segmentType: 'TEXT', text: 'Hello there' });
        expect(segment.type).toBe('TEXT');
        expect(segment.text).toBe('Hello there');
        expect(segment.action).toBeNull();
    });

    it('builds an INPUT segment with type, description and parameters', () => {
        const segment = Segment.fromJSON({
            segmentType: 'INPUT',
            inputType: 'numeric',
            description: 'your age',
            parameters: { min: 0, max: 120 },
        });
        expect(segment.type).toBe('INPUT');
        expect(segment.inputType).toBe('numeric');
        expect(segment.description).toBe('your age');
        expect(segment.parameters).toEqual({ min: 0, max: 120 });
        expect(segment.text).toBe('');
    });

    it('builds an INPUT segment with defaults when optional fields are absent', () => {
        const segment = Segment.fromJSON({ segmentType: 'INPUT', inputType: 'text' });
        expect(segment.description).toBeNull();
        expect(segment.parameters).toEqual({});
    });

    it('builds an ACTION segment wrapping an Action', () => {
        const segment = Segment.fromJSON({
            segmentType: 'ACTION',
            action: { type: 'video', value: 'https://example.test/v.mp4', parameters: {} },
        });
        expect(segment.type).toBe('ACTION');
        expect(segment.action).toBeInstanceOf(Action);
        expect(segment.action.type).toBe('video');
        expect(segment.action.value).toBe('https://example.test/v.mp4');
    });

    it('keeps the plain (type, text) constructor working for fixtures', () => {
        const segment = new Segment('TEXT', 'x');
        expect(segment.type).toBe('TEXT');
        expect(segment.text).toBe('x');
    });
});

describe('Statement segment helpers', () => {
    const statement = new Statement([
        new Segment('TEXT', 'before '),
        Segment.fromJSON({ segmentType: 'INPUT', inputType: 'text', parameters: {} }),
        new Segment('TEXT', ' after'),
    ]);

    it('fullStatement() stays text-only', () => {
        expect(statement.fullStatement()).toBe('before  after');
    });

    it('hasSegmentOfType / segmentsOfType report the non-text segments', () => {
        expect(statement.hasSegmentOfType('INPUT')).toBe(true);
        expect(statement.hasSegmentOfType('ACTION')).toBe(false);
        expect(statement.segmentsOfType('INPUT')).toHaveLength(1);
        expect(statement.segmentsOfType('TEXT')).toHaveLength(2);
    });
});

describe('DialogueBranchClient.createDialogueStepObject', () => {
    const client = new DialogueBranchClient('/api/v1');

    const step = client.createDialogueStepObject({
        dialogue: 'inputs',
        node: 'Start',
        speaker: 'Martin McOwl',
        loggedDialogueId: 'ld-1',
        loggedInteractionIndex: 0,
        statement: {
            segments: [
                { segmentType: 'TEXT', text: 'Here is a picture.' },
                {
                    segmentType: 'ACTION',
                    action: { type: 'image', value: 'dog.png', parameters: {} },
                },
            ],
        },
        replies: [
            {
                replyId: 1,
                endsDialogue: false,
                statement: {
                    segments: [
                        { segmentType: 'TEXT', text: 'My name is ' },
                        {
                            segmentType: 'INPUT',
                            inputType: 'text',
                            description: 'your first name',
                            parameters: { min: 2, max: 30 },
                        },
                        { segmentType: 'TEXT', text: '.' },
                    ],
                },
                actions: [
                    { type: 'generic', value: 'PLAY_SOUND', parameters: { file: 'ding.mp3' } },
                ],
            },
            {
                replyId: 2,
                endsDialogue: true,
                statement: null,
                actions: [],
            },
        ],
    });

    it('parses ACTION segments in the agent statement', () => {
        const actionSegments = step.statement.segmentsOfType('ACTION');
        expect(actionSegments).toHaveLength(1);
        expect(actionSegments[0].action).toBeInstanceOf(Action);
        expect(actionSegments[0].action.type).toBe('image');
        expect(actionSegments[0].action.value).toBe('dog.png');
    });

    it('parses INPUT segments inside a reply statement', () => {
        const basicReply = step.replies[0];
        expect(basicReply).toBeInstanceOf(BasicReply);
        const inputSegments = basicReply.statement.segmentsOfType('INPUT');
        expect(inputSegments).toHaveLength(1);
        expect(inputSegments[0].inputType).toBe('text');
        expect(inputSegments[0].description).toBe('your first name');
        expect(inputSegments[0].parameters).toEqual({ min: 2, max: 30 });
        expect(basicReply.statement.fullStatement()).toBe('My name is .');
    });

    it('unfolds reply actions into Action objects', () => {
        const actions = step.replies[0].actions;
        expect(actions).toHaveLength(1);
        expect(actions[0]).toBeInstanceOf(Action);
        expect(actions[0].type).toBe('generic');
        expect(actions[0].parameter('file')).toBe('ding.mp3');
    });

    it('gives an empty action list (not undefined) when a reply has none', () => {
        const autoForward = step.replies[1];
        expect(autoForward).toBeInstanceOf(AutoForwardReply);
        expect(autoForward.actions).toEqual([]);
    });
});

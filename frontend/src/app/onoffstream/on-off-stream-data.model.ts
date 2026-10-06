export interface OnOffStreamData {
    streaming: boolean;
    tick?: number;
    timestamp?: string;
}

/** Runtime check — TypeScript types vanish at runtime, so this validates actual shape */
export function isOnOffStreamData(value: unknown): value is OnOffStreamData {
    return (
        typeof value === 'object' &&
        value !== null &&
        'streaming' in value &&
        typeof (value as any).streaming === 'boolean'
    );
}
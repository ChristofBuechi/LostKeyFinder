import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { loadAuthConfiguration } from './auth-configuration';

describe('Bounded Auth configuration loading', () => {
  const fetchMock = vi.fn<typeof fetch>();
  const emptyConfiguration = { url: '', publishableKey: '' };
  let controller: AbortController;

  beforeEach(() => {
    controller = new AbortController();
    vi.spyOn(AbortSignal, 'timeout').mockReturnValue(controller.signal);
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('loads configuration with a five-second native timeout', async () => {
    const configuration = { url: 'https://example.supabase.co', publishableKey: 'sb_publishable_test' };
    fetchMock.mockResolvedValue(new Response(JSON.stringify(configuration)));
    expect(await loadAuthConfiguration()).toEqual(configuration);
    expect(AbortSignal.timeout).toHaveBeenCalledWith(5000);
    expect(fetchMock).toHaveBeenCalledWith('/auth-config.json', { cache: 'no-store', signal: controller.signal });
  });

  it.each(['request', 'body'])('falls back when the %s stalls until timeout', async (stage) => {
    fetchMock.mockImplementation((_url, options) => {
      const waitForAbort = () => new Promise<Response>((_resolve, reject) => {
        const signal = options!.signal!;
        signal.addEventListener('abort', () => reject(signal.reason), { once: true });
      });
      return stage === 'request' ? waitForAbort() : Promise.resolve({ ok: true, json: waitForAbort } as unknown as Response);
    });
    const result = loadAuthConfiguration();
    await Promise.resolve();
    controller.abort(new DOMException('Timed out', 'TimeoutError'));
    expect(await result).toEqual(emptyConfiguration);
  });

  it('falls back for unavailable, malformed or failed configuration responses', async () => {
    for (const response of [new Response('', { status: 503 }), new Response('not-json')]) {
      fetchMock.mockResolvedValueOnce(response);
      expect(await loadAuthConfiguration()).toEqual(emptyConfiguration);
    }
    fetchMock.mockRejectedValueOnce(new Error('Network unavailable'));
    expect(await loadAuthConfiguration()).toEqual(emptyConfiguration);
  });
});

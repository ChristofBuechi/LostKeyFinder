import { afterEach, assert, describe, it, expect, vi } from 'vitest';
import { PDFDocument, PDFPage } from 'pdf-lib';
import { toDataURL } from 'qrcode';
import { tagPdf } from './tag-pdf';
vi.mock('qrcode', () => ({ toDataURL: vi.fn(async () => 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==') }));
const id = '550e8400-e29b-41d4-a716-446655440000';
describe('Printable tag PDFs', () => {
  afterEach(() => vi.restoreAllMocks());
  it.each(['https://finder.example', `https://${'long-domain-'.repeat(4)}example.com`])('prints the full URL within the card margins (%s)', async origin => {
    const drawText = vi.spyOn(PDFPage.prototype, 'drawText');
    const url = `${origin}/f/${id}`;
    await tagPdf([id], origin, false, 'Please scan the QR code.');
    const options = drawText.mock.calls.find(([text]) => text === url)?.[1];
    assert(options?.font && options.size && options.x !== undefined, 'Full URL must be drawn with an explicit font, size and position');
    expect(options.size).toBeLessThanOrEqual(7);
    expect(options.x).toBeCloseTo(5 * 72 / 25.4);
    expect(options.x + options.font.widthOfTextAtSize(url, options.size)).toBeLessThanOrEqual((85.6 - 5) * 72 / 25.4 + 0.001);
  });
  it('creates exact credit card pages with existing public links', async () => {
    const data = await tagPdf([id], 'https://finder.example', false, 'Found this?\nPlease scan the QR code.');
    const pdf = await PDFDocument.load(data);
    expect(pdf.getPageCount()).toBe(1);
    expect(pdf.getPage(0).getWidth()).toBeCloseTo(85.6 * 72 / 25.4);
    expect(pdf.getPage(0).getHeight()).toBeCloseTo(53.98 * 72 / 25.4);
    expect(toDataURL).toHaveBeenCalledWith(`https://finder.example/f/${id}`, expect.objectContaining({ margin: 4 }));
  });
  it('paginates A4 sheets at ten cards without dropping tags', async () => {
    const encode = vi.mocked(toDataURL); encode.mockClear();
    const ids = Array.from({ length: 25 }, (_, index) => `${id.slice(0, -2)}${String(index).padStart(2, '0')}`);
    const pdf = await PDFDocument.load(await tagPdf(ids, 'http://localhost:4202', true, 'Gefunden?\nBitte QR-Code scannen.'));
    expect(pdf.getPageCount()).toBe(3);
    expect(pdf.getPage(0).getWidth()).toBeCloseTo(210 * 72 / 25.4);
    expect(pdf.getPage(0).getHeight()).toBeCloseTo(297 * 72 / 25.4);
    expect(encode).toHaveBeenCalledTimes(25);
    expect(encode).toHaveBeenLastCalledWith(`http://localhost:4202/f/${ids[24]}`, expect.anything());
  });
  it('rejects empty and unbounded selections', async () => {
    await expect(tagPdf([], 'https://finder.example', true, '')).rejects.toThrow();
    await expect(tagPdf(Array(26).fill(id), 'https://finder.example', true, '')).rejects.toThrow();
  });
});

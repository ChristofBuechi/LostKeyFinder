import { PDFDocument, StandardFonts, rgb, PrintScaling } from 'pdf-lib';
import { toDataURL } from 'qrcode';

const mm = 72 / 25.4;
const width = 85.6 * mm;
const height = 53.98 * mm;

// Public identity only: private tag labels and owner data never enter print files.
export async function tagPdf(publicIds: string[], origin: string, sheet: boolean, instruction: string): Promise<Uint8Array> {
  if (!publicIds.length || publicIds.length > 25) throw new Error('Select 1–25 tags');
  const pdf = await PDFDocument.create();
  pdf.setTitle('Lost Key Finder');
  pdf.catalog.getOrCreateViewerPreferences().setPrintScaling(PrintScaling.None);
  const font = await pdf.embedFont(StandardFonts.Helvetica);
  for (let index = 0; index < publicIds.length; index++) {
    const slot = sheet ? index % 10 : 0;
    if (slot === 0) pdf.addPage(sheet ? [210 * mm, 297 * mm] : [width, height]);
    const page = pdf.getPages()[pdf.getPageCount() - 1];
    const x = sheet ? (17 + (slot % 2) * 90.6) * mm : 0;
    const y = sheet ? (297 - 8 - (Math.floor(slot / 2) + 1) * 53.98 - Math.floor(slot / 2) * 2) * mm : 0;
    if (sheet) page.drawRectangle({ x, y, width, height, borderWidth: .3, borderColor: rgb(.65, .65, .65) });
    const qr = await pdf.embedPng(await toDataURL(`${origin}/f/${publicIds[index]}`, { width: 768, margin: 4, errorCorrectionLevel: 'M' }));
    page.drawImage(qr, { x: x + 4 * mm, y: y + 9 * mm, width: 34 * mm, height: 34 * mm });
    page.drawText('Lost Key Finder', { x: x + 40 * mm, y: y + 38 * mm, font, size: 12 });
    page.drawText(instruction, { x: x + 40 * mm, y: y + 28 * mm, font, size: 9, maxWidth: 40 * mm, lineHeight: 12 });
    page.drawText(publicIds[index], { x: x + 5 * mm, y: y + 5 * mm, font, size: 7 });
  }
  return pdf.save();
}

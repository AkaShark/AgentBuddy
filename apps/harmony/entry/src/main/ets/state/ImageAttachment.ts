import common from '@ohos.app.ability.common';
import photoAccessHelper from '@ohos.file.photoAccessHelper';
import picker from '@ohos.file.picker';
import fs from '@ohos.file.fs';
import image from '@ohos.multimedia.image';
import util from '@ohos.util';

let nextImageId = 0;
export class ImageAttachment {
  id: string = String(++nextImageId);
  dataUri: string = '';
  width: number = 0;
  height: number = 0;
  bytes: number = 0;
}

export async function pickImages(context: common.UIAbilityContext, fromFiles: boolean, limit: number): Promise<ImageAttachment[]> {
  const uris = fromFiles
    ? await new picker.DocumentViewPicker(context).select({ maxSelectNumber: limit,
      fileSuffixFilters: ['图片|.png,.jpg,.jpeg,.webp,.gif,.heic,.heif'] })
    : (await new photoAccessHelper.PhotoViewPicker().select({
      MIMEType: photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE, maxSelectNumber: limit,
      isPhotoTakingSupported: false })).photoUris;
  const attachments: ImageAttachment[] = [];
  for (const uri of uris) attachments.push(await readImageAttachment(uri));
  return attachments;
}

// Only read URIs explicitly granted by the system picker. As on Android,
// decode/re-encode the image before passing its data URI to Rust's typed input.
// Native image objects are released; no image files or caches are created.
async function readImageAttachment(uri: string): Promise<ImageAttachment> {
  const file = fs.openSync(uri, fs.OpenMode.READ_ONLY);
  try {
    if (fs.statSync(file.fd).size > 32 * 1024 * 1024) throw new Error('图片超过 32 MB，请选择较小的图片。');
    return await encodeImageAttachment(image.createImageSource(file.fd));
  } finally { fs.closeSync(file); }
}

// Restore attachments before asking Rust to remove any history. In particular,
// file:// refers to the host, never a phone file path granted by the picker.
export async function restoreMessageImages(uris: string[], resolve: (path: string) => Promise<Uint8Array>): Promise<ImageAttachment[]> {
  if (uris.length > 4) throw new Error('这条消息超过 4 张图片，请在主机端编辑。');
  const result: ImageAttachment[] = [];
  for (const uri of uris) {
    if (uri.startsWith('file://')) {
      const bytes = await resolve(uri.substring(7));
      if (bytes.byteLength > 32 * 1024 * 1024) throw new Error('原消息图片超过 32 MB，无法恢复到草稿。');
      result.push(await encodeImageAttachment(image.createImageSource(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength))));
    } else if (uri.startsWith('data:image/') || uri.startsWith('https://') || uri.startsWith('http://')) {
      const attachment = new ImageAttachment(); attachment.dataUri = uri; result.push(attachment);
    } else { throw new Error('原消息图片无法恢复，历史未修改。'); }
  }
  return result;
}

async function encodeImageAttachment(source: image.ImageSource): Promise<ImageAttachment> {
  let pixel: image.PixelMap | undefined;
  let packer: image.ImagePacker | undefined;
  try {
    const info = await source.getImageInfo();
    const ratio = Math.min(1, 2048 / Math.max(info.size.width, info.size.height));
    const size: image.Size = { width: Math.max(1, Math.round(info.size.width * ratio)),
      height: Math.max(1, Math.round(info.size.height * ratio)) };
    pixel = await source.createPixelMap({ desiredSize: size });
    const decoded = await pixel.getImageInfo();
    const mime = decoded.alphaType === image.AlphaType.OPAQUE ? 'image/jpeg' : 'image/png';
    packer = image.createImagePacker();
    const bytes = await packer.packing(pixel, { format: mime, quality: 85 });
    if (bytes.byteLength > 10 * 1024 * 1024) throw new Error('处理后的图片仍超过 10 MB，请选择较小的图片。');
    const attachment = new ImageAttachment();
    attachment.dataUri = 'data:' + mime + ';base64,' + new util.Base64Helper().encodeToStringSync(new Uint8Array(bytes));
    attachment.width = decoded.size.width; attachment.height = decoded.size.height; attachment.bytes = bytes.byteLength;
    return attachment;
  } finally {
    await pixel?.release().catch(() => {});
    await source?.release().catch(() => {});
    await packer?.release().catch(() => {});
  }
}

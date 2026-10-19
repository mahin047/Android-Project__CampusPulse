package com.campuspulse.app;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageUtil {

    private static final int MAX_SIDE = 960;           // longest side in pixels
    private static final int MAX_BYTES = 350 * 1024;   // target size after compression

    /** Decodes, rotates (EXIF), shrinks and compresses an image. Run on a background thread. */
    public static byte[] compress(ContentResolver cr, Uri uri) throws IOException {
        // 1. read only the size, so we never load a 12 MP photo fully into memory
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open image");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Not an image");

        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2;

        // 2. decode a down-sampled copy
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open image");
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new IOException("Could not decode image");

        // 3. read the camera rotation
        int rotation = 0;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in != null) {
                ExifInterface exif = new ExifInterface(in);
                int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
                if (o == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
            }
        } catch (IOException ignored) { }

        // 4. scale down + rotate
        float scale = Math.min(1f, (float) MAX_SIDE / Math.max(bmp.getWidth(), bmp.getHeight()));
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        m.postRotate(rotation);
        Bitmap out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        if (out != bmp) bmp.recycle();

        // 5. JPEG, lowering quality until the size fits
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int quality = 75;
        do {
            bos.reset();
            out.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            quality -= 10;
        } while (bos.size() > MAX_BYTES && quality >= 35);
        out.recycle();
        return bos.toByteArray();
    }

    public static String toBase64(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public static Bitmap fromBase64(String b64) {
        byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
}
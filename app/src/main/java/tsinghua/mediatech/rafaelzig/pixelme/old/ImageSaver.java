package tsinghua.mediatech.rafaelzig.pixelme.old;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Saves a JPEG {@link Image} into the specified {@link File}.
 */

public class ImageSaver implements Runnable
{
	private final Image image;
	private final File  file;

	public ImageSaver(Image image, File file)
	{
		this.image = image;
		image.getPlanes();
		this.file = file;
	}

	@Override
	public void run()
	{
		try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file)))
		{
			out.write(getRGBfromYUV());
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			image.close();
		}
	}

	private byte[] getRGBfromYUV()
	{
		int height = image.getHeight();
		int width = image.getWidth();

		ByteBuffer[] byteBuffers = new ByteBuffer[image.getPlanes().length];
		int[] rowStrides = new int[byteBuffers.length];
		byte[][] bytes = new byte[byteBuffers.length][];

		for (int i = 0; i < byteBuffers.length; i++)
		{
			byteBuffers[i] = image.getPlanes()[i].getBuffer();
			rowStrides[i] = image.getPlanes()[i].getRowStride();
			bytes[i] = new byte[byteBuffers[i].remaining()];
			byteBuffers[i].get(bytes[i]);
		}

		final int BYTES_PER_RGB_PIXEL = 3;
		byte[] yuv = { 0, 0, 0 };
		byte[] rgb = new byte[width * BYTES_PER_RGB_PIXEL];

		// For Android 5.0.1(API 21) has an issue which is blank(zero) U, V arrays except the first some bytes(eg. 656 bytes for 176x144).
		// This issue caused the converted image turned to Green scaled overall of the image.
		// This issue is fixed in Android 5.1.1(API 22).

		for (int i = 0, y = 0, u, v; i < height; i++)
		{
			u = (i >> 1) * rowStrides[1];
			v = (i >> 1) * rowStrides[2];

			for (int j = 0; j < width; j++, y++)
			{
				yuv[0] = bytes[0][y];

				if ((j & 1) == 0)
				{
					yuv[1] = bytes[1][u++];
					yuv[2] = bytes[2][v++];
				}

				yuvToRgb(yuv, j * BYTES_PER_RGB_PIXEL, rgb);
			}
		}

		return rgb;
	}

	private void yuvToRgb(byte[] yuv, int rgbOffset, byte[] rgb)
	{
		float y = yuv[0] & 0xff; // Y channel
		float u = yuv[1] & 0xff; // U channel
		float v = yuv[2] & 0xff; // V channel

		// Convert YUV fixed pixel to RGB (from JFIF's "Conversion to and from RGB" section).
		float r = y + 1.402f * (v - 128);
		float g = y - 0.34414f * (u - 128) - 0.71414f * (v - 128);
		float b = y + 1.772f * (u - 128);

		// Clamp to [0, 255].
		final int RGB_MAX = 255;
		r = Math.max(0, Math.min(RGB_MAX, r));
		g = Math.max(0, Math.min(RGB_MAX, g));
		b = Math.max(0, Math.min(RGB_MAX, b));

		// 'byte' is signed, it takes the last 8bits of the integer.
		rgb[rgbOffset] = (byte) ((int) r);
		rgb[rgbOffset + 1] = (byte) ((int) g);
		rgb[rgbOffset + 2] = (byte) ((int) b);
	}
}
package tsinghua.mediatech.rafaelzig.pixelme;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Class containing static methods for convenience when using pictures.
 *
 * @author Rafael da Silva Costa - 2015280364
 * @version 02/11/15
 */
public class ColorUtils
{

	public static final float MAX_8BIT_COLORS = 255f;

	/**
	 * Returns the combined RGB components of the supplied parameter.
	 *
	 * @param rgb The integer array containing the red, green and blue colour
	 *            components.
	 * @return Combined RGB components of the supplied parameter.
	 */
	public static int getRGB(int[] rgb)
	{
		return getRGB(rgb[0], rgb[1], rgb[2]);
	}

	/**
	 * Returns the combined RGB components of the supplied parameters.
	 *
	 * @param r The red component.
	 * @param g The green component.
	 * @param b The blue component.
	 * @return Combined RGB components of the supplied parameter.
	 */
	public static int getRGB(int r, int g, int b)
	{
		return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | ((b & 0xFF) << 0);
	}

	public static int getRGB(int aRGB)
	{
		return getRGB(Color.red(aRGB), Color.green(aRGB), Color.blue(aRGB));
	}

	/**
	 * Returns the combined aRGB components of the supplied parameters.
	 *
	 * @param a The alpha component.
	 * @param r The red component.
	 * @param g The green component.
	 * @param b The blue component.
	 * @return Combined aRGB components of the supplied parameter.
	 */
	public static int getARGB(int a, int r, int g, int b)
	{
		return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8)
				| ((b & 0xFF) << 0);
	}

	/**
	 * Returns the combined aRGB components with default alpha values of the
	 * supplied parameters.
	 *
	 * @param r The red component.
	 * @param g The green component.
	 * @param b The blue component.
	 * @return Combined aRGB components with default alpha values of the
	 * supplied parameter.
	 */
	public static int getARGB(int r, int g, int b)
	{
		return (0xFF << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | ((b & 0xFF) << 0);
	}

	/**
	 * Returns the combined aRGB components with default alpha values of the
	 * supplied parameters.
	 *
	 * @param rgb Combined RGB components.
	 * @return Combined aRGB components with default alpha values of the
	 * supplied parameter.
	 */
	public static int getARGB(int rgb)
	{
		return (0xFF << 24) | rgb;
	}

	public static int getQuantizedColor(int rgb, int bits)
	{
		float maxColors = (float) (Math.pow(2, bits) - 1);
		float ratio = maxColors / MAX_8BIT_COLORS;
		int range = Math.round(MAX_8BIT_COLORS / maxColors);

		int temp = 0;

		for (int j = 0; j <= 2; j++)
		{
			int before = (rgb >> 8 * j) & 0xFF;
			int reduced = Math.round(before * ratio);
			int after = range * reduced;
			temp = (after << 8 * j) | temp;
		}

		return getARGB(temp);
	}

	/**
	 * Partitions the provided Bitmap into blocks of {@code blockSize}
	 * and returns a 3D array of integers containing the aRGB components of
	 * every pixel in the input.
	 *
	 * @param image The Bitmap object to be parsed.
	 * @return 3D array of integers containing the aRGB components of every
	 * pixel in the input, where {@code array[x][y][z]} retrieves the
	 * aRGB component of pixel {@code z} in block {@code xy}.
	 */
	public static Bitmap transform(Bitmap image, int blockSize, int colorBits)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		int pixelsPerBlock = (int) Math.pow(blockSize, 2);
		int blocksPerRow = width / blockSize;
		int blocksPerCol = height / blockSize;

		int[][][] blocks = new int[blocksPerRow][blocksPerCol][pixelsPerBlock];

		for (int x = 0; x < blocksPerRow; x++)
		{
			for (int y = 0; y < blocksPerCol; y++)
			{
				// Create one block
				image.getPixels(blocks[x][y], 0, blockSize, x * blockSize, y * blockSize, blockSize, blockSize);

				int redSum = 0;
				int greenSum = 0;
				int blueSum = 0;

				// Calculate sum of color components in the block to find the average color
				for (int z = 0; z < pixelsPerBlock; z++)
				{
					redSum += Color.red(blocks[x][y][z]);
					greenSum += Color.green(blocks[x][y][z]);
					blueSum += Color.blue(blocks[x][y][z]);
				}

				int avg = getARGB(redSum / pixelsPerBlock, greenSum / pixelsPerBlock, blueSum / pixelsPerBlock);
				int quantized = getQuantizedColor(avg, colorBits);

				// Paint the average colour in every pixel of the block
				for (int z = 0; z < pixelsPerBlock; z++)
				{
					blocks[x][y][z] = quantized;
				}

				image.setPixels(blocks[x][y], 0, blockSize, x * blockSize, y * blockSize, blockSize, blockSize);
			}
		}

		return image;
	}
}
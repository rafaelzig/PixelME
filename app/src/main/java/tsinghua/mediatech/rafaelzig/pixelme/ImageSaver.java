package tsinghua.mediatech.rafaelzig.pixelme;

import android.media.Image;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Saves a JPEG {@link Image} into the specified {@link File}.
 */

public class ImageSaver implements Runnable
{
	/**
	 * The JPEG image
	 */
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
		ByteBuffer buffer = image.getPlanes()[0].getBuffer();
		byte[] bytes = new byte[buffer.remaining()];
		buffer.get(bytes);

		try (FileOutputStream out = new FileOutputStream(file))
		{
			out.write(bytes);
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
}

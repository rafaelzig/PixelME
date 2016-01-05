package tsinghua.mediatech.rafaelzig.pixelme.camera.component;

import android.media.Image;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Observable;

/**
 * Saves a JPEG {@link Image} into the specified {@link File}.
 */
public class ImageSaver extends Observable implements Runnable
{
	/**
	 * The JPEG image
	 */
	private final Image image;
	/**
	 * The file we save the image into.
	 */
	private final File  file;

	public ImageSaver(Image image, File file)
	{
		this.image = image;
		this.file = file;
	}

	@Override
	public void run()
	{
		ByteBuffer buffer = image.getPlanes()[0].getBuffer();
		byte[] bytes = new byte[buffer.remaining()];
		buffer.get(bytes);
		try (FileOutputStream output = new FileOutputStream(file))
		{
			output.write(bytes);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			image.close();
			setChanged();
			notifyObservers(file.getAbsolutePath());
		}
	}
}
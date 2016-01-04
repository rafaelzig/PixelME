package tsinghua.mediatech.rafaelzig.pixelme.camera.component;

import android.media.Image;
import android.os.Handler;
import android.os.Message;

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
	private final Image mImage;
	/**
	 * The file we save the image into.
	 */
	private final File  mFile;

	private final Handler uiHandler;

	public ImageSaver(Image image, File file, Handler uiHandler)
	{
		mImage = image;
		mFile = file;
		this.uiHandler = uiHandler;
	}

	@Override
	public void run()
	{
		ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
		byte[] bytes = new byte[buffer.remaining()];
		buffer.get(bytes);
		try(FileOutputStream output = new FileOutputStream(mFile))
		{
			output.write(bytes);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			mImage.close();
			uiHandler.obtainMessage().sendToTarget();
		}
	}
}
package tsinghua.mediatech.rafaelzig.pixelme.camera.component;

import android.media.Image;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Saves a JPEG {@link Image} into the specified {@link File}.
 */
public class ImageSaver implements Runnable
{
	private static final String TAG      = ImageSaver.class.getSimpleName();
	public static final  int    COMPLETE = 1;
	public static final  int    ERROR    = -2;
	/**
	 * The JPEG image
	 */
	private final Image   image;
	/**
	 * The file we save the image into.
	 */
	private final File    file;
	private final Handler handler;

	public ImageSaver(Image image, File file, Handler handler)
	{
		this.image = image;
		this.file = file;
		this.handler = handler;
	}

	@Override
	public void run()
	{
		ByteBuffer buffer = image.getPlanes()[0].getBuffer();
		byte[] bytes = new byte[buffer.remaining()];
		buffer.get(bytes);

		String errorMessage = new String();

		try (FileOutputStream output = new FileOutputStream(file))
		{
			output.write(bytes);
		}
		catch (IOException e)
		{
			errorMessage = e.getMessage();
			Log.e(TAG, errorMessage);
		}
		finally
		{
			image.close();

			Message message = (errorMessage.isEmpty()) ?
			                  handler.obtainMessage(COMPLETE, file.getAbsolutePath()) :
			                  handler.obtainMessage(ERROR);
			message.sendToTarget();
		}
	}
}
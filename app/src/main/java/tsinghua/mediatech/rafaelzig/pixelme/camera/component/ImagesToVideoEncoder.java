package tsinghua.mediatech.rafaelzig.pixelme.camera.component;

import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import tsinghua.mediatech.rafaelzig.pixelme.AndroidMp4;

import java.io.File;
import java.io.IOException;

/**
 * Created by Zig on 06/01/2016.
 */
public class ImagesToVideoEncoder implements Runnable
{
	private static final String TAG           = ImagesToVideoEncoder.class.getSimpleName();
	public static final  int    IMAGE_ENCODED = 2;
	public static final  int    ERROR         = -1;
	public static final  int    COMPLETE      = 3;
	private final File[]  input;
	private final File    outputFile;
	private final Handler handler;

	public ImagesToVideoEncoder(File[] input, File output, Handler handler)
	{
		this.input = input;
		this.outputFile = output;
		this.handler = handler;
	}

	@Override
	public void run()
	{
		AndroidMp4 encoder = null;
		String errorMessage = new String();

		try
		{
			encoder = new AndroidMp4(outputFile, input.length);

			for (File file : input)
			{
				encoder.encodeImage(file);
				handler.obtainMessage(IMAGE_ENCODED).sendToTarget();
			}
		}
		catch (IOException e)
		{
			errorMessage = e.getMessage();
			Log.e(TAG, errorMessage);
		}
		finally
		{
			try
			{
				encoder.finish();
			}
			catch (IOException e)
			{
				errorMessage = e.getMessage();
				Log.e(TAG, errorMessage);
			}
			finally
			{
				Message message = (errorMessage.isEmpty()) ?
				                  handler.obtainMessage(COMPLETE, outputFile.getAbsolutePath()) :
				                  handler.obtainMessage(ERROR, errorMessage);
				message.sendToTarget();
			}
		}
	}
}
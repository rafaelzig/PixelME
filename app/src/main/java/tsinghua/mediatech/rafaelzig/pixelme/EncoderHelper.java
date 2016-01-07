package tsinghua.mediatech.rafaelzig.pixelme;

import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * Created by gabri on 12/10/2015.
 */
public class EncoderHelper
{
	private File outputFile;
	private File inputDir;

	public EncoderHelper(File inputDir, File outputFile)
	{
		this.inputDir = inputDir;
		this.outputFile = outputFile;
	}

	/**
	 * It will create a mp4 video with the images contained in the directory attribute with the fps send in parameter.
	 *
	 * @param fps the frame per second (I advise you to not send less than 6).
	 */
	public void encode(int fps) throws IOException
	{
		AndroidMp4 enc = new AndroidMp4(outputFile, fps);

		for (File f : inputDir.listFiles())
		{
			enc.encodeImage(f);
			Log.d("EncoderHelper", f.getAbsolutePath() + " encoded!");
		}

		enc.finish();
	}
}
package tsinghua.mediatech.rafaelzig.pixelme.camera.component;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.util.Pair;
import android.widget.ImageView;
import tsinghua.mediatech.rafaelzig.pixelme.ImageUtils;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * Created by Zig on 20/12/2015.
 */
public class BitmapWorkerTask extends AsyncTask<File, Void, Bitmap>
{
	private       WeakReference<ImageView> imageViewReference;
	private final int                      width;
	private final int                      height;

	public BitmapWorkerTask(ImageView imageView, int width, int height)
	{
		imageViewReference = new WeakReference<>(imageView);
		this.width = width;
		this.height = height;
	}

	@Override
	protected Bitmap doInBackground(File... imageFiles)
	{
		return ImageUtils.decodeAndTransform(imageFiles[0], width, height);
	}

	@Override
	protected void onPostExecute(Bitmap image)
	{
		if (image != null && imageViewReference != null)
		{
			ImageView imageView = imageViewReference.get();

			if (imageView != null)
			{
				imageView.setImageBitmap(image);
			}
		}
	}
}
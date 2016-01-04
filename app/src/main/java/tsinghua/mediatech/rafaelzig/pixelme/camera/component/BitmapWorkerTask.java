package tsinghua.mediatech.rafaelzig.pixelme.camera.component;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.os.Build;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * Created by Zig on 20/12/2015.
 */
public class BitmapWorkerTask extends AsyncTask<File, Void, Bitmap>
{
	private       File                     imageFile;
	private       WeakReference<ImageView> imageViewReference;
	private final int                      width;
	private final int                      height;
	private Matrix matrix;

	public BitmapWorkerTask(ImageView imageView, int width, int height)
	{
		imageViewReference = new WeakReference<>(imageView);
		this.width = width;
		this.height = height;
	}

	@Override
	protected Bitmap doInBackground(File... params)
	{
		imageFile = params[0];
		return decodeBitmapFromFile(imageFile);
	}

	@Override
	protected void onPostExecute(Bitmap image)
	{
		if (image != null && imageViewReference != null)
		{
			ImageView imageView = imageViewReference.get();

			if (imageView != null)
			{
				imageView.setImageBitmap(Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true));
			}
		}
	}

	private int calculateInSampleSize(int width, int height)
	{
		int scaleFactor = 1;
		if (width > this.width || height > this.height)
		{
			int halfWidth = width / 2, halfHeight = height / 2;

			while (halfWidth / scaleFactor > this.width || halfHeight / scaleFactor > this.height)
				scaleFactor *= 2;
		}

		return scaleFactor;
	}

	private Bitmap decodeBitmapFromFile(File imageFile)
	{
		String fileLocation = imageFile.getAbsolutePath();

		BitmapFactory.Options bmOptions = new BitmapFactory.Options();
		bmOptions.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(fileLocation, bmOptions);

		bmOptions.inSampleSize = calculateInSampleSize(bmOptions.outWidth, bmOptions.outHeight);
		bmOptions.inJustDecodeBounds = false;
		calculateMatrix(fileLocation);

		return BitmapFactory.decodeFile(fileLocation, bmOptions);
	}

	private void calculateMatrix(String fileLocation)
	{
		matrix = new Matrix();
		int orientation = 1;

		try
		{
			orientation = new ExifInterface(fileLocation).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		if (orientation == ExifInterface.ORIENTATION_ROTATE_90)
			matrix.postRotate(90);
		else if (orientation == ExifInterface.ORIENTATION_ROTATE_270)
			matrix.postRotate(270);
	}

	File getImageFile()
	{
		return imageFile;
	}
}
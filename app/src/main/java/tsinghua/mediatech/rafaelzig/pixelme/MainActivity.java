package tsinghua.mediatech.rafaelzig.pixelme;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity
{
	static final int REQUEST_IMAGE_CAPTURE = 1;
	String mCurrentPhotoPath;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
	}

	public void dispatchTakePictureIntent(View v)
	{
		Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		// Ensure that there's a camera activity to handle the intent
		if (takePictureIntent.resolveActivity(getPackageManager()) != null)
		{
			// Create the File where the photo should go
			try
			{
				Uri uri = Uri.fromFile(createImageFile());
				takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
				startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	private File createImageFile() throws IOException
	{
		// Create an image file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String prefix = "PixelME_" + timeStamp;
		File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		File image = File.createTempFile(prefix, ".jpg", directory);

		// Save a file: path for use with ACTION_VIEW intents
		mCurrentPhotoPath = image.getAbsolutePath();
		return image;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK)
		{
			setImage();
			galleryAddPic();
		}
	}

	private void setImage()
	{
		ImageView mImageView = (ImageView) findViewById(R.id.mImageView);

		// Get the dimensions of the View
		int targetW = mImageView.getWidth();
		int targetH = mImageView.getHeight();

		// Get the dimensions of the bitmap
		BitmapFactory.Options bmOptions = new BitmapFactory.Options();
//		bmOptions.inJustDecodeBounds = true;
		Bitmap image = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
		int photoW = bmOptions.outWidth;
		int photoH = bmOptions.outHeight;

		// Determine how much to scale down the image
		int scaleFactor = Math.min(photoW / targetW, photoH / targetH);

		// Decode the image file into a Bitmap sized to fill the View
		bmOptions.inJustDecodeBounds = false;
		bmOptions.inSampleSize = scaleFactor;

		image = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
		image = transform(image, DitherFilter.dither90Halftone6x6Matrix, 2, true);
		mImageView.setImageBitmap(image);
	}

	private void galleryAddPic()
	{
		Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		Uri contentUri = Uri.fromFile(new File(mCurrentPhotoPath));
		mediaScanIntent.setData(contentUri);
		sendBroadcast(mediaScanIntent);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		//noinspection SimplifiableIfStatement
		if (id == R.id.action_settings)
		{
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private static Bitmap transform(Bitmap input, int[] matrix, int levels, boolean isColor)
	{
		int width = input.getWidth();
		int height = input.getHeight();

		DitherFilter df = new DitherFilter();
		df.setLevels(levels);
		df.setMatrix(matrix);
		df.setColorDither(isColor);

		Bitmap output = Bitmap.createBitmap(width, height, input.getConfig());

		for (int x = 0; x < width; x++)
		{
			for (int y = 0; y < height; y++)
			{
				output.setPixel(x, y, df.filterRGB(x, y, input.getPixel(x, y)));
			}
		}

		return output;
	}
}
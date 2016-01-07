package tsinghua.mediatech.rafaelzig.pixelme.old;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import tsinghua.mediatech.rafaelzig.pixelme.ImageUtils;
import tsinghua.mediatech.rafaelzig.pixelme.R;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener
{
	String mCurrentPhotoPath;
	static final  int REQUEST_IMAGE_CAPTURE = 1;
	private SeekBar skbPixelSize, skbBits;
	private TextView lblPixelSize, lblBitsPerColor;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.old_activity_main);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		skbBits = (SeekBar) findViewById(R.id.skbBits);
		skbBits.setOnSeekBarChangeListener(this);
		lblBitsPerColor = (TextView) findViewById(R.id.lblBitsPerColor);
		skbPixelSize = (SeekBar) findViewById(R.id.skbPixelSize);
		skbPixelSize.setOnSeekBarChangeListener(this);
		lblPixelSize = (TextView) findViewById(R.id.lblPixelSize);
	}

	public void onClick(View v)
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
		String timeStamp = SimpleDateFormat.getDateTimeInstance().format(new Date());
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
		bmOptions.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
		int photoW = bmOptions.outWidth;
		int photoH = bmOptions.outHeight;

		// Determine how much to scale down the image
		int scaleFactor = Math.min(photoW / targetW, photoH / targetH);

		// Decode the image file into a Bitmap sized to fill the View
		bmOptions.inJustDecodeBounds = false;
		bmOptions.inSampleSize = scaleFactor;
		bmOptions.inMutable = true;

		Bitmap image = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
		ImageUtils.transform(image, Integer.parseInt(lblPixelSize.getText()
		                                                         .toString()), Integer.parseInt(lblBitsPerColor.getText()
		                                                                                                       .toString()));
		mImageView.setImageBitmap(Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), getMatrix(), true));
	}

	@NonNull
	private Matrix getMatrix()
	{
		Matrix matrix = new Matrix();
		int orientation = 1;

		try
		{
			orientation = new ExifInterface(mCurrentPhotoPath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		if (orientation == ExifInterface.ORIENTATION_ROTATE_90)
			matrix.postRotate(90);
		else if (orientation == ExifInterface.ORIENTATION_ROTATE_270)
			matrix.postRotate(270);

		return matrix;
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

	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		if (seekBar.equals(skbBits))
		{
			lblBitsPerColor.setText(String.valueOf(progress + 1));
		}
		else
		{
			lblPixelSize.setText(String.valueOf((int) Math.pow(2, progress + 1)));
		}
	}

	public void onStartTrackingTouch(SeekBar seekBar)
	{
	}

	public void onStopTrackingTouch(SeekBar seekBar)
	{
	}
}
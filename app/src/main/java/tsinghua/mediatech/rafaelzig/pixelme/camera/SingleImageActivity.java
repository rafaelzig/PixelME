package tsinghua.mediatech.rafaelzig.pixelme.camera;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import tsinghua.mediatech.rafaelzig.pixelme.R;
import tsinghua.mediatech.rafaelzig.pixelme.camera.component.BitmapWorkerTask;

import java.io.File;

/**
 * Created by Zig on 01/01/2016.
 */
public class SingleImageActivity extends Activity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		DisplayMetrics displayMetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

		ImageView imageView = new ImageView(this);
		imageView.setBackgroundColor(Color.BLACK);
		int width = displayMetrics.widthPixels;
		int height = displayMetrics.heightPixels;
		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(width, height);
		imageView.setLayoutParams(params);

		setContentView(imageView);

		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		int macroblockSize = sharedPref.getInt(SettingsActivity.PREFERENCE_MACROBLOCK_SIZE, R.integer.macroblock_size_default_value);
		int colorBits = sharedPref.getInt(SettingsActivity.PREFERENCE_COLOR_BITS, R.integer.color_bits_default_value);

		File imageFile = new File(getIntent().getStringExtra(CameraFragment.FILE_LOCATION_EXTRAS_KEY));
		new BitmapWorkerTask(imageView, width, height, macroblockSize, colorBits).execute(imageFile);
	}
}
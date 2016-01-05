package tsinghua.mediatech.rafaelzig.pixelme.camera;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.ImageView;
import android.widget.RelativeLayout;
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

		File imageFile = new File(getIntent().getStringExtra(CameraFragment.FILE_LOCATION));
		new BitmapWorkerTask(imageView, width, height).execute(imageFile);
	}
}
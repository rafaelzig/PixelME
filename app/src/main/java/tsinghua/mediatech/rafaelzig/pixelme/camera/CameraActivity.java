package tsinghua.mediatech.rafaelzig.pixelme.camera;

import android.app.Activity;
import android.os.Bundle;
import tsinghua.mediatech.rafaelzig.pixelme.R;

public class CameraActivity extends Activity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera);
		if (savedInstanceState == null)
		{
			getFragmentManager().beginTransaction()
			                    .replace(R.id.container, CameraFragment.newInstance())
			                    .commit();
		}
	}
}
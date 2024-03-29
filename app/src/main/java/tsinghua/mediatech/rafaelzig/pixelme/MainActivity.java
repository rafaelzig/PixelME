package tsinghua.mediatech.rafaelzig.pixelme;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.Toast;
import tsinghua.mediatech.rafaelzig.pixelme.camera.CameraActivity;
import tsinghua.mediatech.rafaelzig.pixelme.camera.CameraFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements FeedAdapterListener
{
	public static final int REQUEST_CODE = 1;
	ListView listView;
	DBHelper mydb;
	FeedAdapter feedAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main2);

		listView = (ListView) findViewById(R.id.feed);

		mydb = new DBHelper(this);

//        mydb.deleteAll();



		ArrayList<Map<String, String>> arrayList = mydb.getAllEntries();

		feedAdapter = new FeedAdapter(this, arrayList);
		feedAdapter.addFeedAdapterListener(this);

		listView.setAdapter(feedAdapter);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.menu_main2, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.camera:
				openCameraActivity();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void openCameraActivity()
	{
		File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
		Intent intent = new Intent(this, CameraActivity.class);
		startActivityForResult(intent, REQUEST_CODE);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		// Check which request we're responding to and ensure the request was successful
		if (requestCode == REQUEST_CODE && resultCode == RESULT_OK)
		{
			if (data.hasExtra(CameraFragment.FILE_LOCATION_EXTRAS_KEY))
			{
				String uri = data.getExtras().getString(CameraFragment.FILE_LOCATION_EXTRAS_KEY);
				mydb.insertEntry(uri);
				feedAdapter.AddVideoToFeed(mydb.getLastEntry());
			}
		}
	}

	@Override
	public void didDeleteEntry(int entry_id)
	{
		mydb.deleteEntry(entry_id);
	}
}
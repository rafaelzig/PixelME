package tsinghua.mediatech.rafaelzig.pixelme;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import tsinghua.mediatech.rafaelzig.pixelme.camera.CameraActivity;

import java.util.ArrayList;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements FeedAdapterListener
{
	ListView listView;
	DBHelper mydb;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main2);

		listView = (ListView) findViewById(R.id.feed);

		mydb = new DBHelper(this);
/*
        mydb.deleteAll();

        mydb.insertEntry("android.resource://"+getPackageName()+ "/"+R.raw.video1);
        mydb.insertEntry("android.resource://"+getPackageName()+ "/"+R.raw.video2);
        mydb.insertEntry("android.resource://"+getPackageName()+ "/"+R.raw.video3);
        mydb.insertEntry("android.resource://"+getPackageName()+ "/"+R.raw.video4);
        mydb.insertEntry("android.resource://"+getPackageName()+ "/"+R.raw.video5);

*/

		ArrayList<Map<String, String>> arrayList = mydb.getAllEntries();

		FeedAdapter feedAdapter = new FeedAdapter(this, arrayList);
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
				Intent intent = new Intent(this, CameraActivity.class);
				startActivity(intent);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void didDeleteEntry(int entry_id)
	{
		mydb.deleteEntry(entry_id);
	}
}
package tsinghua.mediatech.rafaelzig.pixelme.camera;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import tsinghua.mediatech.rafaelzig.pixelme.R;

/**
 * Created by Zig on 08/01/2016.
 */
public class SettingsActivity extends Activity
{
	public static final String PREFERENCE_MACROBLOCK_SIZE = "preference_macroblock_size";
	public static final String PREFERENCE_COLOR_BITS      = "preference_color_bits";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Display the fragment as the main content.
		getFragmentManager().beginTransaction()
		                    .replace(android.R.id.content, new SettingsFragment())
		                    .commit();
	}

	public static class SettingsFragment extends PreferenceFragment
	{
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			// Load the preferences from an XML resource
			addPreferencesFromResource(R.xml.preferences);
		}
	}
}
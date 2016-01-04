package tsinghua.mediatech.rafaelzig.pixelme.camera.dialog;

import android.Manifest;
import android.app.*;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v13.app.FragmentCompat;
import tsinghua.mediatech.rafaelzig.pixelme.R;
import tsinghua.mediatech.rafaelzig.pixelme.camera.Camera2BasicFragment;

/**
 * Shows OK/Cancel confirmation dialog about camera permission.
 */
public class ConfirmationDialog extends DialogFragment
{
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		final Fragment parent = getParentFragment();
		return new AlertDialog.Builder(getActivity())
				.setMessage(R.string.request_permission)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						FragmentCompat.requestPermissions(parent,
						                                  new String[]{Manifest.permission.CAMERA},
						                                  Camera2BasicFragment.REQUEST_CAMERA_PERMISSION);
					}
				})
				.setNegativeButton(android.R.string.cancel,
				                   new DialogInterface.OnClickListener()
				                   {
					                   @Override
					                   public void onClick(DialogInterface dialog, int which)
					                   {
						                   Activity activity = parent.getActivity();
						                   if (activity != null)
						                   {
							                   activity.finish();
						                   }
					                   }
				                   })
				.create();
	}
}
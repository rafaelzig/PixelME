package tsinghua.mediatech.rafaelzig.pixelme.camera.dialog;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

/**
 * A {@link android.preference.Preference} that displays a number numberPicker as a dialog.
 */
public class MacroblocksPreference extends DialogPreference
{
	private final        boolean isWrapSelectorWheel = false;
	private static final int     DEF_VALUE           = 8;
	private static final int     MIN_VALUE           = 2;
	private static final int     MAX_VALUE           = 16;
	private int          value;
	private NumberPicker numberPicker;

	public MacroblocksPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	public MacroblocksPreference(Context context, AttributeSet attrs, int defStyleAttr)
	{
		super(context, attrs, defStyleAttr);
	}

	@Override
	protected View onCreateDialogView()
	{
		FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		layoutParams.gravity = Gravity.CENTER;

		numberPicker = new NumberPicker(getContext());
		numberPicker.setLayoutParams(layoutParams);

		FrameLayout dialogView = new FrameLayout(getContext());
		dialogView.addView(numberPicker);

		return dialogView;
	}

	@Override
	protected void onBindDialogView(View view)
	{
		super.onBindDialogView(view);
		numberPicker.setMinValue(MIN_VALUE);
		numberPicker.setMaxValue(MAX_VALUE);
		numberPicker.setWrapSelectorWheel(isWrapSelectorWheel);
		numberPicker.setValue(getValue());
	}

	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		if (positiveResult)
		{
			numberPicker.clearFocus();
			int newValue = numberPicker.getValue();

			if (callChangeListener(newValue))
			{
				setValue(newValue);
			}
		}
	}

	@Override
	protected Parcelable onSaveInstanceState()
	{
		final Parcelable superState = super.onSaveInstanceState();
		// Check whether this Preference is persistent (continually saved)
		if (isPersistent())
		{
			// No need to save instance state since it's persistent,
			// use superclass state
			return superState;
		}

		// Create instance of custom BaseSavedState
		final SavedState myState = new SavedState(superState);
		// Set the state's value with the class member that holds current
		// setting value
		myState.value = value;
		return myState;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state)
	{
		// Check whether we saved the state in onSaveInstanceState
		if (state == null || !state.getClass().equals(SavedState.class))
		{
			// Didn't save the state, so call superclass
			super.onRestoreInstanceState(state);
			return;
		}

		// Cast state to custom BaseSavedState and pass to superclass
		SavedState myState = (SavedState) state;
		super.onRestoreInstanceState(myState.getSuperState());

		// Set this Preference's widget to reflect the restored state
		numberPicker.setValue(myState.value);
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index)
	{
		return a.getInt(index, DEF_VALUE);
	}

	@Override
	protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue)
	{
		if (restorePersistedValue)
		{
			value = getPersistedInt(DEF_VALUE);
		}
		else
		{
			setValue((Integer) defaultValue);
		}
	}

	public void setValue(int value)
	{
		this.value = value;
		persistInt(this.value);
	}

	public int getValue()
	{
		return this.value;
	}

	private static class SavedState extends BaseSavedState
	{
		// Member that holds the setting's value
		// Change this data type to match the type saved by your Preference
		int value;

		public SavedState(Parcelable superState)
		{
			super(superState);
		}

		public SavedState(Parcel source)
		{
			super(source);
			// Get the current preference's value
			value = source.readInt();
		}

		@Override
		public void writeToParcel(Parcel dest, int flags)
		{
			super.writeToParcel(dest, flags);
			// Write the preference's value
			dest.writeInt(value);
		}

		// Standard creator object using an instance of this class
		public static final Creator<SavedState> CREATOR =
				new Creator<SavedState>()
				{
					public SavedState createFromParcel(Parcel in)
					{
						return new SavedState(in);
					}

					public SavedState[] newArray(int size)
					{
						return new SavedState[size];
					}
				};
	}
}
package tsinghua.mediatech.rafaelzig.pixelme.camera;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.os.*;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.*;
import android.view.animation.AnimationUtils;
import android.widget.Toast;
import tsinghua.mediatech.rafaelzig.pixelme.MainActivity;
import tsinghua.mediatech.rafaelzig.pixelme.R;
import tsinghua.mediatech.rafaelzig.pixelme.camera.component.*;
import tsinghua.mediatech.rafaelzig.pixelme.camera.dialog.ConfirmationDialog;
import tsinghua.mediatech.rafaelzig.pixelme.camera.dialog.ErrorDialog;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CameraFragment extends Fragment
		implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback, RecyclerViewClickObserver
{
	public static final String FILE_LOCATION_EXTRAS_KEY = CameraFragment.class.getCanonicalName() + ".ImageFileLocationExtrasKey";
	public static final String CURRENT_CAMERA           = "current camera";
	public static final String JPG_EXTENSION            = ".jpg";
	public static final String MP4_EXTENSION            = ".mp4";
	public static final int    PREVIEW_READY            = 6;
	public static final int    TAKING_PICTURE           = 4;
	public static final int    IMAGE_OUTPUT_HEIGHT      = 640;
	public static final int    IMAGE_OUTPUT_WIDTH       = 480;
	private File galleryFolder;

	/**
	 * Conversion from screen rotation to JPEG orientation.
	 */
	private static final SparseIntArray BACK_CAM_ORIENTATIONS     = new SparseIntArray();
	private static final SparseIntArray FRONT_CAM_ORIENTATIONS    = new SparseIntArray();
	public static final  int            REQUEST_CAMERA_PERMISSION = 1;
	private static final String         FRAGMENT_DIALOG           = "dialog";

	static
	{
		BACK_CAM_ORIENTATIONS.append(Surface.ROTATION_0, 90);
		BACK_CAM_ORIENTATIONS.append(Surface.ROTATION_90, 0);
		BACK_CAM_ORIENTATIONS.append(Surface.ROTATION_180, 270);
		BACK_CAM_ORIENTATIONS.append(Surface.ROTATION_270, 180);

		FRONT_CAM_ORIENTATIONS.append(Surface.ROTATION_0, 270);
		FRONT_CAM_ORIENTATIONS.append(Surface.ROTATION_90, 0);
		FRONT_CAM_ORIENTATIONS.append(Surface.ROTATION_180, 90);
		FRONT_CAM_ORIENTATIONS.append(Surface.ROTATION_270, 180);
	}

	/**
	 * Tag for the {@link Log}.
	 */
	private static final String TAG = "PixelME";

	/**
	 * Camera state: Showing camera preview.
	 */
	private static final int STATE_PREVIEW = 0;

	/**
	 * Camera state: Waiting for the focus to be locked.
	 */
	private static final int STATE_WAITING_LOCK = 1;

	/**
	 * Camera state: Waiting for the exposure to be precapture state.
	 */
	private static final int STATE_WAITING_PRECAPTURE = 2;

	/**
	 * Camera state: Waiting for the exposure state to be something other than precapture.
	 */
	private static final int STATE_WAITING_NON_PRECAPTURE = 3;

	/**
	 * Camera state: Picture was taken.
	 */
	private static final int STATE_PICTURE_TAKEN = 4;

	/**
	 * Max preview width that is guaranteed by Camera2 API
	 */
	private static final int MAX_PREVIEW_WIDTH = 1920;

	/**
	 * Max preview height that is guaranteed by Camera2 API
	 */
	private static final int MAX_PREVIEW_HEIGHT = 1080;

	/**
	 * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
	 * {@link TextureView}.
	 */
	private final TextureView.SurfaceTextureListener surfaceTextureListener
			= new TextureView.SurfaceTextureListener()
	{

		@Override
		public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height)
		{
			openCamera(width, height);
		}

		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height)
		{
			configureTransform(width, height);
		}

		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture texture)
		{
			return true;
		}

		@Override
		public void onSurfaceTextureUpdated(SurfaceTexture texture)
		{
		}
	};

	/**
	 * ID of the current {@link CameraDevice}.
	 */
	private String cameraId;

	/**
	 * An {@link AutoFitTextureView} for camera preview.
	 */
	private AutoFitTextureView textureView;

	/**
	 * A {@link CameraCaptureSession } for camera preview.
	 */
	private CameraCaptureSession captureSession;

	/**
	 * A reference to the opened {@link CameraDevice}.
	 */
	private CameraDevice cameraDevice;

	/**
	 * The {@link android.util.Size} of camera preview.
	 */
	private Size previewSize;

	/**
	 * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
	 */
	private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback()
	{
		@Override
		public void onOpened(@NonNull CameraDevice cameraDevice)
		{
			// This method is called when the camera is opened.  We start camera preview here.
			cameraOpenCloseLock.release();
			CameraFragment.this.cameraDevice = cameraDevice;
			createCameraPreviewSession();
		}

		@Override
		public void onDisconnected(@NonNull CameraDevice cameraDevice)
		{
			cameraOpenCloseLock.release();
			cameraDevice.close();
			CameraFragment.this.cameraDevice = null;
		}

		@Override
		public void onError(@NonNull CameraDevice cameraDevice, int error)
		{
			cameraOpenCloseLock.release();
			cameraDevice.close();
			CameraFragment.this.cameraDevice = null;
			Activity activity = getActivity();
			if (null != activity)
			{
				activity.finish();
			}
		}
	};

	/**
	 * An additional thread for running tasks that shouldn't block the UI.
	 */
	private HandlerThread backgroundThread;

	/**
	 * A {@link Handler} for running tasks in the background.
	 */
	private Handler backgroundHandler;

	private final Handler uiHandler = new HelperHandler(this);

	/**
	 * An {@link ImageReader} that handles still image capture.
	 */
	private ImageReader imageReader;

	/**
	 * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
	 * still image is ready to be saved.
	 */
	private final ImageReader.OnImageAvailableListener onImageAvailableListener
			= new ImageReader.OnImageAvailableListener()
	{
		@Override
		public void onImageAvailable(ImageReader reader)
		{
			ImageSaver imageSaver = new ImageSaver(reader.acquireNextImage(), createUniqueFile(galleryFolder, JPG_EXTENSION), uiHandler);
			backgroundHandler.post(imageSaver);
		}
	};

	/**
	 * {@link CaptureRequest.Builder} for the camera preview
	 */
	private CaptureRequest.Builder previewRequestBuilder;

	/**
	 * {@link CaptureRequest} generated by {@link #previewRequestBuilder}
	 */
	private CaptureRequest previewRequest;

	/**
	 * The current state of camera state for taking pictures.
	 *
	 * @see #captureCallback
	 */
	private int state = STATE_PREVIEW;

	/**
	 * A {@link Semaphore} to prevent the app from exiting before closing the camera.
	 */
	private Semaphore cameraOpenCloseLock = new Semaphore(1);

	private MediaActionSound mediaActionSound;
	/**
	 * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
	 */
	private final CameraCaptureSession.CaptureCallback captureCallback
			= new CameraCaptureSession.CaptureCallback()
	{
		private void process(CaptureResult result)
		{
			switch (state)
			{
				case STATE_PREVIEW:
				{
					// We have nothing to do when the camera preview is working normally.
					break;
				}
				case STATE_WAITING_LOCK:
				{
					Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);

					if (afState == null || afState == CaptureResult.CONTROL_AF_STATE_INACTIVE)
					{
						state = STATE_PICTURE_TAKEN;
						captureStillPicture();
					}
					else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
							afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED)
					{
						// CONTROL_AE_STATE can be null on some devices
						Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
						if (aeState == null ||
								aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED)
						{
							state = STATE_PICTURE_TAKEN;
							captureStillPicture();
						}
						else
						{
							runPrecaptureSequence();
						}
					}

					break;
				}
				case STATE_WAITING_PRECAPTURE:
				{
					// CONTROL_AE_STATE can be null on some devices
					Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
					if (aeState == null ||
							aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
							aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED)
					{
						state = STATE_WAITING_NON_PRECAPTURE;
					}

					break;
				}
				case STATE_WAITING_NON_PRECAPTURE:
				{
					// CONTROL_AE_STATE can be null on some devices
					Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
					if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE)
					{
						state = STATE_PICTURE_TAKEN;
						captureStillPicture();
					}
					break;
				}
			}
		}

		@Override
		public void onCaptureProgressed(@NonNull CameraCaptureSession session,
				@NonNull CaptureRequest request,
				@NonNull CaptureResult partialResult)
		{
			process(partialResult);
		}

		@Override
		public void onCaptureCompleted(@NonNull CameraCaptureSession session,
				@NonNull CaptureRequest request,
				@NonNull TotalCaptureResult result)
		{
			process(result);
		}
	};

	private ImageAdapter      imageAdapter;
	private GridLayoutManager layoutManager;

	/**
	 * Current camera being used
	 */
	private int            currentCamera;
	private ProgressDialog progressDialog;
	private View           btnCapture;
	private View           btnEncode;
	private View           btnSwap;
	private View           btnSettings;

	/**
	 * Shows a {@link Toast} on the UI thread.
	 *
	 * @param text The message to show
	 */
	private void showToast(final String text)
	{
		final Activity activity = getActivity();
		if (activity != null)
		{
			activity.runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
				}
			});
		}
	}

	/**
	 * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
	 * is at least as large as the respective texture view size, and that is at most as large as the
	 * respective max size, and whose aspect ratio matches with the specified value. If such size
	 * doesn't exist, choose the largest one that is at most as large as the respective max size,
	 * and whose aspect ratio matches with the specified value.
	 *
	 * @param choices           The list of sizes that the camera supports for the intended output
	 *                          class
	 * @param textureViewWidth  The width of the texture view relative to sensor coordinate
	 * @param textureViewHeight The height of the texture view relative to sensor coordinate
	 * @param maxWidth          The maximum width that can be chosen
	 * @param maxHeight         The maximum height that can be chosen
	 * @param aspectRatio       The aspect ratio
	 * @return The optimal {@code Size}, or an arbitrary one if none were big enough
	 */
	private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
			int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio)
	{

		// Collect the supported resolutions that are at least as big as the preview Surface
		List<Size> bigEnough = new ArrayList<>();
		// Collect the supported resolutions that are smaller than the preview Surface
		List<Size> notBigEnough = new ArrayList<>();
		int w = aspectRatio.getWidth();
		int h = aspectRatio.getHeight();
		for (Size option : choices)
		{
			if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
					option.getHeight() == option.getWidth() * h / w)
			{
				if (option.getWidth() >= textureViewWidth &&
						option.getHeight() >= textureViewHeight)
				{
					bigEnough.add(option);
				}
				else
				{
					notBigEnough.add(option);
				}
			}
		}

		// Pick the smallest of those big enough. If there is no one big enough, pick the
		// largest of those not big enough.
		if (bigEnough.size() > 0)
		{
			return Collections.min(bigEnough, new CompareSizesByArea());
		}
		else if (notBigEnough.size() > 0)
		{
			return Collections.max(notBigEnough, new CompareSizesByArea());
		}
		else
		{
			Log.e(TAG, "Couldn't find any suitable preview size");
			return choices[0];
		}
	}

	public static CameraFragment newInstance()
	{
		return new CameraFragment();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.fragment_camera, container, false);
	}

	@Override
	public void onViewCreated(final View view, Bundle savedInstanceState)
	{
		initializeInstanceVariables(savedInstanceState);
		setWidgets(view);
	}

	private void setWidgets(View view)
	{
		setRecyclerView(view);
		btnSwap = view.findViewById(R.id.btnSwap);
		btnSwap.setEnabled(false);
		btnSwap.setOnClickListener(this);
		btnCapture = view.findViewById(R.id.btnCapture);
		btnCapture.setEnabled(false);
		btnCapture.setOnClickListener(this);
		btnEncode = view.findViewById(R.id.btnEncode);
		btnEncode.setEnabled(false);
		btnEncode.setOnClickListener(this);
		btnSettings = view.findViewById(R.id.btnSettings);
		btnSettings.setEnabled(false);
		btnSettings.setOnClickListener(this);
		textureView = (AutoFitTextureView) view.findViewById(R.id.textureView);
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState)
	{
		super.onSaveInstanceState(savedInstanceState);
		savedInstanceState.putInt(CURRENT_CAMERA, currentCamera);
	}

	private void initializeInstanceVariables(Bundle savedInstanceState)
	{
		galleryFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), TAG);

		if (savedInstanceState == null)
		{
			if (!galleryFolder.exists())
			{
				galleryFolder.mkdirs();
			}
			else
			{
				clearImageGallery();
			}

			currentCamera = CameraCharacteristics.LENS_FACING_FRONT;
		}
		else
		{
			currentCamera = (int) savedInstanceState.get(CURRENT_CAMERA);
		}
	}

	private void clearImageGallery()
	{
		for (File file : galleryFolder.listFiles())
			file.delete();
	}

	private void setRecyclerView(View view)
	{
		RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView);
		imageAdapter = new ImageAdapter(galleryFolder.listFiles(), this);
		recyclerView.setAdapter(imageAdapter);

		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
		{
			layoutManager = new GridLayoutManager(view.getContext(), 1, GridLayoutManager.HORIZONTAL, false);
			registerTouchHelper(recyclerView, true);
		}
		else
		{
			layoutManager = new GridLayoutManager(view.getContext(), 1, GridLayoutManager.VERTICAL, false);
			registerTouchHelper(recyclerView, false);
		}

		recyclerView.setLayoutManager(layoutManager);
	}

	private void registerTouchHelper(RecyclerView recyclerView, boolean isHorizontal)
	{
		new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, (isHorizontal) ?
		                                                          ItemTouchHelper.DOWN :
		                                                          ItemTouchHelper.RIGHT)
		{
			@Override
			public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target)
			{
				return false;
			}

			@Override
			public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir)
			{
				imageAdapter.removeImage(viewHolder.getAdapterPosition());
			}
		}).attachToRecyclerView(recyclerView);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		startBackgroundThread();

		// When the screen is turned off and turned back on, the SurfaceTexture is already
		// available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
		// a camera and start preview from here (otherwise, we wait until the surface is ready in
		// the SurfaceTextureListener).
		if (textureView.isAvailable())
		{
			openCamera(textureView.getWidth(), textureView.getHeight());
		}
		else
		{
			textureView.setSurfaceTextureListener(surfaceTextureListener);
		}
	}

	@Override
	public void onPause()
	{
		flickButtons(false);
		closeCamera();
		stopBackgroundThread();
		super.onPause();
	}

	private void closeCamera()
	{
		try
		{
			cameraOpenCloseLock.acquire();
			if (captureSession != null)
			{
				captureSession.close();
				captureSession = null;
			}
			if (cameraDevice != null)
			{
				cameraDevice.close();
				cameraDevice = null;
			}
			if (imageReader != null)
			{
				imageReader.close();
				imageReader = null;
			}
			if (mediaActionSound != null)
			{
				mediaActionSound.release();
				mediaActionSound = null;
			}
		}
		catch (InterruptedException e)
		{
			throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
		}
		finally
		{
			cameraOpenCloseLock.release();
		}
	}

	private File createUniqueFile(File directory, String extension)
	{
		String filename = new SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(new Date()) + extension;
		return new File(directory, filename);
	}

	private void requestCameraPermission()
	{
		if (FragmentCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA))
		{
			new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
		}
		else
		{
			FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
			                                  REQUEST_CAMERA_PERMISSION);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
			@NonNull int[] grantResults)
	{
		if (requestCode == REQUEST_CAMERA_PERMISSION)
		{
			if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED)
			{
				ErrorDialog.newInstance(getString(R.string.request_permission))
				           .show(getChildFragmentManager(), FRAGMENT_DIALOG);
			}
		}
		else
		{
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}

	/**
	 * Sets up member variables related to camera.
	 *
	 * @param width  The width of available size for camera preview
	 * @param height The height of available size for camera preview
	 */
	private void setupCamera(int width, int height)
	{
		Activity activity = getActivity();
		CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

		try
		{
			for (String cameraId : manager.getCameraIdList())
			{
				CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

				if (characteristics.get(CameraCharacteristics.LENS_FACING) == currentCamera)
				{
					StreamConfigurationMap map = characteristics.get(
							CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

					Size common;

					if (map != null && (common = get640480(map.getOutputSizes(ImageFormat.JPEG))) != null)
					{
//						Size smallest = Collections.min(collection,
//						                                new CompareSizesByArea());
						imageReader = ImageReader.newInstance(common.getWidth(), common.getHeight(),
						                                      ImageFormat.JPEG, 2);
						imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

						Point displaySize = new Point();
						activity.getWindowManager().getDefaultDisplay().getSize(displaySize);

						// Swap dimensions to get the preview size relative to sensor coordinate if needed
						if (isRotated(activity, characteristics))
						{
							int temp = width;
							width = height;
							height = temp;
							displaySize.set(displaySize.y, displaySize.x);
						}

						if (displaySize.x > MAX_PREVIEW_WIDTH)
						{
							displaySize.x = MAX_PREVIEW_WIDTH;
						}

						if (displaySize.y > MAX_PREVIEW_HEIGHT)
						{
							displaySize.y = MAX_PREVIEW_HEIGHT;
						}

						// Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
						// bus' bandwidth limitation, resulting in gorgeous previews but the storage of
						// garbage capture data.
						previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
						                                width, height, displaySize.x,
						                                displaySize.y, common);

						// We fit the aspect ratio of TextureView to the size of preview we picked.
						int orientation = getResources().getConfiguration().orientation;
						if (orientation == Configuration.ORIENTATION_LANDSCAPE)
							textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
						else
							textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());

						// Face recognition test
//				int maxCount = characteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);
//				int modes [] = characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);

						this.cameraId = cameraId;
						return;
					}
				}
			}
		}

		catch (CameraAccessException e)
		{
			e.printStackTrace();
		}

		catch (NullPointerException e)
		{
			// Currently an NPE is thrown when the Camera2API is used but not supported
			ErrorDialog.newInstance(getString(R.string.camera_error))
			           .show(getChildFragmentManager(), FRAGMENT_DIALOG);
		}
	}

	private Size get640480(Size[] outputSizes)
	{
		Comparator<Size> sizeComparator = new SizeComparator();
		Arrays.sort(outputSizes, sizeComparator);
		int i = Arrays.binarySearch(outputSizes, new Size(IMAGE_OUTPUT_HEIGHT, IMAGE_OUTPUT_WIDTH), sizeComparator);

		Size size = null;

		if (i >= 0)
		{
			size = outputSizes[i];
		}

		return size;
	}

	private boolean isRotated(Activity activity, CameraCharacteristics characteristics)
	{
		int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
		int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

		switch (displayRotation)
		{
			case Surface.ROTATION_0:
			case Surface.ROTATION_180:
				if (sensorOrientation == 90 || sensorOrientation == 270)
					return true;
				break;
			case Surface.ROTATION_90:
			case Surface.ROTATION_270:
				if (sensorOrientation == 0 || sensorOrientation == 180)
					return true;
				break;
			default:
				Log.e(TAG, "Display rotation is invalid: " + displayRotation);
		}

		return false;
	}

	/**
	 * Opens the camera specified by {@link CameraFragment#cameraId}.
	 */
	private void openCamera(int width, int height)
	{
		if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
		{
			requestCameraPermission();
			return;
		}

		mediaActionSound = new MediaActionSound();
		mediaActionSound.load(MediaActionSound.SHUTTER_CLICK);
		mediaActionSound.load(MediaActionSound.FOCUS_COMPLETE);
		mediaActionSound.load(MediaActionSound.START_VIDEO_RECORDING);
		mediaActionSound.load(MediaActionSound.STOP_VIDEO_RECORDING);

		setupCamera(width, height);
		configureTransform(width, height);
		CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);

		try
		{
			if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS))
			{
				throw new RuntimeException("Time out waiting to lock camera opening.");
			}
			manager.openCamera(cameraId, stateCallback, backgroundHandler);
		}
		catch (CameraAccessException e)
		{
			e.printStackTrace();
		}
		catch (InterruptedException e)
		{
			throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
		}
	}

	private void flickButtons(boolean isEnabled)
	{
		btnSwap.setEnabled(isEnabled);
		btnCapture.setEnabled(isEnabled);
		btnEncode.setEnabled(isEnabled);
		btnSettings.setEnabled(isEnabled);
	}

	private void flickButtons()
	{
		btnSwap.setEnabled(!btnSwap.isEnabled());
		btnCapture.setEnabled(!btnCapture.isEnabled());
		btnEncode.setEnabled(!btnEncode.isEnabled());
		btnSettings.setEnabled(!btnSettings.isEnabled());
	}

	/**
	 * Starts a background thread and its {@link Handler}.
	 */
	private void startBackgroundThread()
	{
		new Handler(Looper.getMainLooper());
		backgroundThread = new HandlerThread("CameraBackground");
		backgroundThread.start();
		backgroundHandler = new Handler(backgroundThread.getLooper());
	}

	/**
	 * Stops the background thread and its {@link Handler}.
	 */
	private void stopBackgroundThread()
	{
		backgroundThread.quitSafely();
		try
		{
			backgroundThread.join();
			backgroundThread = null;
			backgroundHandler = null;
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Creates a new {@link CameraCaptureSession} for camera preview.
	 */
	private void createCameraPreviewSession()
	{
		try
		{
			SurfaceTexture texture = textureView.getSurfaceTexture();
			assert texture != null;

			// We configure the size of default buffer to be the size of camera preview we want.
			texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

			// This is the output Surface we need to start preview.
			Surface surface = new Surface(texture);

			// We set up a CaptureRequest.Builder with the output Surface.
			previewRequestBuilder
					= cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			previewRequestBuilder.addTarget(surface);

			// Here, we create a CameraCaptureSession for camera preview.
			cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
			                                  new CameraCaptureSession.StateCallback()
			                                  {

				                                  @Override
				                                  public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession)
				                                  {
					                                  // The camera is open
					                                  if (cameraDevice != null)
					                                  {
						                                  // When the session is ready, we start displaying the preview.
						                                  captureSession = cameraCaptureSession;
						                                  try
						                                  {
							                                  if (currentCamera == CameraCharacteristics.LENS_FACING_BACK)
							                                  {
								                                  // Auto focus should be continuous for camera preview.
								                                  previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
								                                                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
								                                  // Flash is automatically enabled when necessary.
								                                  previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
								                                                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
							                                  }

							                                  // Finally, we start displaying the camera preview.
							                                  previewRequest = previewRequestBuilder.build();
							                                  captureSession.setRepeatingRequest(previewRequest,
							                                                                     captureCallback, backgroundHandler);
							                                  uiHandler.obtainMessage(PREVIEW_READY).sendToTarget();
						                                  }
						                                  catch (CameraAccessException e)
						                                  {
							                                  e.printStackTrace();
						                                  }
					                                  }
				                                  }

				                                  @Override
				                                  public void onConfigureFailed(
						                                  @NonNull CameraCaptureSession cameraCaptureSession)
				                                  {
					                                  showToast("Failed");
				                                  }
			                                  }, null
			);
		}
		catch (CameraAccessException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Configures the necessary {@link android.graphics.Matrix} transformation to `textureView`.
	 * This method should be called after the camera preview size is determined in
	 * setupCamera and also the size of `textureView` is fixed.
	 *
	 * @param viewWidth  The width of `textureView`
	 * @param viewHeight The height of `textureView`
	 */
	private void configureTransform(int viewWidth, int viewHeight)
	{
		Activity activity = getActivity();

		if (textureView != null && previewSize != null && activity != null)
		{
			int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
			Matrix matrix = new Matrix();
			RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
			RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
			float centerX = viewRect.centerX();
			float centerY = viewRect.centerY();
			if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation)
			{
				bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
				matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
				float scale = Math.max(
						(float) viewHeight / previewSize.getHeight(),
						(float) viewWidth / previewSize.getWidth());
				matrix.postScale(scale, scale, centerX, centerY);
				matrix.postRotate(90 * (rotation - 2), centerX, centerY);
			}
			else if (Surface.ROTATION_180 == rotation)
			{
				matrix.postRotate(180, centerX, centerY);
			}
			textureView.setTransform(matrix);
		}
	}

	/**
	 * Initiate a still image capture.
	 */
	private void takePicture()
	{
		lockFocus();
	}

	/**
	 * Lock the focus as the first step for a still image capture.
	 */
	private void lockFocus()
	{
		try
		{
			// This is how to tell the camera to lock focus.
			previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
			                          CameraMetadata.CONTROL_AF_TRIGGER_START);

			// Tell #captureCallback to wait for the lock.
			state = STATE_WAITING_LOCK;

			captureSession.capture(previewRequestBuilder.build(), captureCallback,
			                       backgroundHandler);
		}
		catch (CameraAccessException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Run the precapture sequence for capturing a still image. This method should be called when
	 * we get a response in {@link #captureCallback} from {@link #lockFocus()}.
	 */
	private void runPrecaptureSequence()
	{
		try
		{
			// This is how to tell the camera to trigger.
			previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
			                          CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
			// Tell #captureCallback to wait for the precapture sequence to be set.
			state = STATE_WAITING_PRECAPTURE;
			captureSession.capture(previewRequestBuilder.build(), captureCallback,
			                       backgroundHandler);
		}
		catch (CameraAccessException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Capture a still picture. This method should be called when we get a response in
	 * {@link #captureCallback} from both {@link #lockFocus()}.
	 */
	private void captureStillPicture()
	{
		uiHandler.obtainMessage(TAKING_PICTURE).sendToTarget();

		Activity activity = getActivity();

		if (activity != null && cameraDevice != null)
		{
			try
			{
				// This is the CaptureRequest.Builder that we use to take a picture.
				final CaptureRequest.Builder captureBuilder =
						cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
				captureBuilder.addTarget(imageReader.getSurface());

				if (currentCamera == CameraCharacteristics.LENS_FACING_BACK)
				{
					// Use the same AE and AF modes as the preview if supported.
					captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
					                   CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
					captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
					                   CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
				}

				// Orientation
				int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
				rotation = (currentCamera == CameraCharacteristics.LENS_FACING_BACK) ?
				           BACK_CAM_ORIENTATIONS.get(rotation) :
				           FRONT_CAM_ORIENTATIONS.get(rotation);

				captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, rotation);

				CameraCaptureSession.CaptureCallback CaptureCallback
						= new CameraCaptureSession.CaptureCallback()
				{
					@Override
					public void onCaptureCompleted(@NonNull CameraCaptureSession session,
							@NonNull CaptureRequest request,
							@NonNull TotalCaptureResult result)
					{
						mediaActionSound.play(MediaActionSound.SHUTTER_CLICK);
						flickButtons();
						unlockFocus();
					}
				};

				captureSession.stopRepeating();
				captureSession.capture(captureBuilder.build(), CaptureCallback, new Handler(activity.getMainLooper()));
			}
			catch (CameraAccessException e)
			{
				e.printStackTrace();
			}
		}
	}

	/**
	 * Unlock the focus. This method should be called when still image capture sequence is
	 * finished.
	 */
	private void unlockFocus()
	{
		try
		{
			// Reset the auto-focus trigger
			previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
			                          CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
			previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
			                          CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
			captureSession.capture(previewRequestBuilder.build(), captureCallback,
			                       backgroundHandler);
			// After this, the camera will go back to the normal state of preview.
			state = STATE_PREVIEW;
			captureSession.setRepeatingRequest(previewRequest, captureCallback,
			                                   backgroundHandler);
		}
		catch (CameraAccessException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void onClick(View view)
	{
		view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
		view.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.rotate));

		switch (view.getId())
		{
			case R.id.btnSwap:
				swapCamera();
				break;
			case R.id.btnCapture:
				takePicture();
				break;
			case R.id.btnEncode:
				encodeVideo();
				break;
			case R.id.btnSettings:
				mediaActionSound.play(MediaActionSound.STOP_VIDEO_RECORDING);
				startActivity(new Intent(getActivity(), SettingsActivity.class));
				break;
		}
	}

	private void swapCamera()
	{
		flickButtons();
		mediaActionSound.play(MediaActionSound.FOCUS_COMPLETE);
		currentCamera = currentCamera == CameraCharacteristics.LENS_FACING_BACK ?
		                CameraCharacteristics.LENS_FACING_FRONT :
		                CameraCharacteristics.LENS_FACING_BACK;

		backgroundHandler.post(new CameraCloser(cameraOpenCloseLock, captureSession, cameraDevice, imageReader, mediaActionSound, uiHandler));
	}

	private void encodeVideo()
	{
		File[] images = galleryFolder.listFiles();

		if (images.length > 0)
		{
			flickButtons();
			mediaActionSound.play(MediaActionSound.START_VIDEO_RECORDING);

			File outputFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), TAG);

			if (!outputFolder.exists())
			{
				outputFolder.mkdirs();
			}

			SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
			int macroblockSize = sharedPref.getInt(SettingsActivity.PREFERENCE_MACROBLOCK_SIZE, R.integer.macroblock_size_default_value);
			int colorBits = sharedPref.getInt(SettingsActivity.PREFERENCE_COLOR_BITS, R.integer.color_bits_default_value);

			displayProgressDialog(images.length);
			backgroundHandler.post(new ImagesToVideoEncoder(images, createUniqueFile(outputFolder, MP4_EXTENSION), uiHandler, macroblockSize, colorBits));
		}
		else
		{
			showToast("Please capture images first.");
		}
	}

	private void displayProgressDialog(int imageCount)
	{
		// prepare for a progress bar dialog
		progressDialog = new ProgressDialog(getActivity());
		progressDialog.setCancelable(false);
		progressDialog.setTitle("Encoding Video ...");
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setProgress(0);
		progressDialog.setMax(imageCount);
		progressDialog.show();
	}

	private void sendVideoToParent(String filePath)
	{
		Activity activity = getActivity();
		Intent sendFileLocationIntent = new Intent(activity, MainActivity.class);
		sendFileLocationIntent.putExtra(FILE_LOCATION_EXTRAS_KEY, filePath);
		activity.setResult(Activity.RESULT_OK, sendFileLocationIntent);
		activity.finish();
	}

	@Override
	public void notifyImageHolderClicked(String imagePath)
	{
		flickButtons();
		Intent sendFileLocationIntent = new Intent(getActivity(), SingleImageActivity.class);
		sendFileLocationIntent.putExtra(FILE_LOCATION_EXTRAS_KEY, imagePath);
		startActivity(sendFileLocationIntent);
	}

	/**
	 * Compares two {@code Size}s based on their areas.
	 */
	private static class CompareSizesByArea implements Comparator<Size>
	{
		@Override
		public int compare(Size lhs, Size rhs)
		{
			// We cast here to ensure the multiplications won't overflow
			return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
					                   (long) rhs.getWidth() * rhs.getHeight());
		}
	}

	private static class HelperHandler extends Handler
	{
		private final WeakReference<CameraFragment> weakReference;

		private HelperHandler(CameraFragment parent)
		{
			super();
			weakReference = new WeakReference<>(parent);
		}

		@Override
		public void handleMessage(Message msg)
		{
			super.handleMessage(msg);

			CameraFragment parent = weakReference.get();
			if (parent != null)
			{
				switch (msg.what)
				{
					case ImageSaver.COMPLETE:
						parent.imageAdapter.addImage(msg.obj.toString());
						parent.layoutManager.scrollToPosition(parent.layoutManager.getItemCount() - 1);
						break;
					case ImagesToVideoEncoder.IMAGE_ENCODED:
						parent.progressDialog.incrementProgressBy(1);
						break;
					case ImagesToVideoEncoder.COMPLETE:
						parent.progressDialog.dismiss();
						parent.flickButtons();
						parent.clearImageGallery();
						parent.sendVideoToParent(msg.obj.toString());
						break;
					case CameraFragment.TAKING_PICTURE:
						parent.flickButtons();
						break;
					case CameraCloser.CAMERA_CLOSED:
						parent.openCamera(parent.textureView.getWidth(), parent.textureView.getHeight());
						break;
					case CameraFragment.PREVIEW_READY:
						parent.flickButtons();
						break;
					case ImagesToVideoEncoder.ERROR:
						parent.flickButtons();
						parent.progressDialog.dismiss();
						parent.getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
						parent.showToast(msg.obj.toString());
						break;
					case ImageSaver.ERROR:
						parent.showToast(msg.obj.toString());
						break;
					case CameraCloser.ERROR:
						parent.showToast(msg.obj.toString());
						break;
				}
			}
		}
	}

	private static class SizeComparator implements Comparator<Size>
	{
		@Override
		public int compare(Size lhs, Size rhs)
		{
			return Integer.compare(lhs.getHeight(), rhs.getHeight());
		}
	}
}
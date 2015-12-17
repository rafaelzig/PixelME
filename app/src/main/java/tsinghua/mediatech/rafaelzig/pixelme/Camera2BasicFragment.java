package tsinghua.mediatech.rafaelzig.pixelme;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.*;
import android.widget.Toast;

import java.io.File;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Camera2BasicFragment extends Fragment
		implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback
{
	/**
	 * Conversion from screen rotation to JPEG orientation.
	 */
	private static final SparseIntArray ORIENTATIONS              = new SparseIntArray();
	static final         int            REQUEST_CAMERA_PERMISSION = 1;
	private static final String         FRAGMENT_DIALOG           = "dialog";
	public static final  int            MAX_IMAGES                = 30;

	static
	{
		ORIENTATIONS.append(Surface.ROTATION_0, 90);
		ORIENTATIONS.append(Surface.ROTATION_90, 0);
		ORIENTATIONS.append(Surface.ROTATION_180, 270);
		ORIENTATIONS.append(Surface.ROTATION_270, 180);
	}

	/**
	 * Tag for the {@link Log}.
	 */
	private static final String TAG = "Camera2BasicFragment";

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
	private static final int STATE_WAITING_PRE_CAPTURE = 2;

	/**
	 * Camera state: Waiting for the exposure state to be something other than precapture.
	 */
	private static final int STATE_WAITING_NON_PRE_CAPTURE = 3;

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
	 * The {@link Size} of camera preview.
	 */
	private Size previewSize;

	/**
	 * Counter used during burst capture
	 */
	private int counter;

	/**
	 * Current camera being used
	 */
	private int currentCamera = CameraCharacteristics.LENS_FACING_FRONT;

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
			Camera2BasicFragment.this.cameraDevice = cameraDevice;
			createPreviewSession();
		}

		@Override
		public void onDisconnected(@NonNull CameraDevice cameraDevice)
		{
			cameraOpenCloseLock.release();
			cameraDevice.close();
			Camera2BasicFragment.this.cameraDevice = null;
		}

		@Override
		public void onError(@NonNull CameraDevice cameraDevice, int error)
		{
			cameraOpenCloseLock.release();
			cameraDevice.close();
			Camera2BasicFragment.this.cameraDevice = null;
			Activity activity = getActivity();
			if (activity != null)
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
			backgroundHandler.post(new ImageSaver(reader.acquireNextImage(), new File(getActivity().getExternalFilesDir(null), counter + ".tmp")));
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

	/**
	 * A {@link CameraCaptureSession.CaptureCallback} that handles events related to picture capture.
	 */
	private CameraCaptureSession.CaptureCallback captureCallback
			= new CameraCaptureSession.CaptureCallback()
	{
		private void process(CaptureResult result)
		{
//			Integer mode = result.get(CaptureResult.STATISTICS_FACE_DETECT_MODE);
//			Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
//			if (faces != null && mode != null)
//				showToast("faces : " + faces.length + " , mode : " + mode);

			switch (state)
			{
				// We have nothing to do when the camera preview is working normally.
				case STATE_PREVIEW:
					break;
				case STATE_WAITING_LOCK:
				{
					Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
					if (afState == null)
					{
//						captureStillPicture();
						captureBurstSequence();
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
//							captureStillPicture();
							captureBurstSequence();
						}
						else
							runPreCaptureSequence();
					}
					break;
				}
				case STATE_WAITING_PRE_CAPTURE:
				{
					// CONTROL_AE_STATE can be null on some devices
					Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
					if (aeState == null ||
							aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
							aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED)
					{
						state = STATE_WAITING_NON_PRE_CAPTURE;
					}
					break;
				}
				case STATE_WAITING_NON_PRE_CAPTURE:
				{
					// CONTROL_AE_STATE can be null on some devices
					Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
					if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE)
					{
						state = STATE_PICTURE_TAKEN;
//						captureStillPicture();
						captureBurstSequence();
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

	/**
	 * Shows a {@link Toast} on the UI thread.
	 *
	 * @param text The message to show
	 */
	private void showToast(final String text)
	{
		final Activity activity = getActivity();
		if (activity != null)
			activity.runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
				}
			});
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
					bigEnough.add(option);
				else
					notBigEnough.add(option);
			}
		}

		// Pick the smallest of those big enough. If there is no one big enough, pick the
		// largest of those not big enough.
		if (bigEnough.size() > 0)
			return Collections.min(bigEnough, new CompareSizesByArea());
		else if (notBigEnough.size() > 0)
			return Collections.max(notBigEnough, new CompareSizesByArea());
		else
		{
			Log.e(TAG, "Couldn't find any suitable preview size");
			return choices[0];
		}
	}

	public static Camera2BasicFragment newInstance()
	{
		return new Camera2BasicFragment();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
	}

	@Override
	public void onViewCreated(final View view, Bundle savedInstanceState)
	{
		view.findViewById(R.id.capture).setOnClickListener(this);
		view.findViewById(R.id.switchCam).setOnClickListener(this);
		textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
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
			openCamera(textureView.getWidth(), textureView.getHeight());
		else
			textureView.setSurfaceTextureListener(surfaceTextureListener);
	}

	@Override
	public void onPause()
	{
		closeCamera();
		stopBackgroundThread();
		super.onPause();
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
				CameraCharacteristics characteristics
						= manager.getCameraCharacteristics(cameraId);

				if (characteristics.get(CameraCharacteristics.LENS_FACING) != currentCamera)
					continue;

				StreamConfigurationMap map = characteristics.get(
						CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
				if (map == null)
					continue;

				// For still image captures, we use the largest available size.
				Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
				                               new CompareSizesByArea());
				imageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
				                                      ImageFormat.YUV_420_888, 10);
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
					displaySize.x = MAX_PREVIEW_WIDTH;

				if (displaySize.y > MAX_PREVIEW_HEIGHT)
					displaySize.y = MAX_PREVIEW_HEIGHT;

				// Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
				// bus' bandwidth limitation, resulting in gorgeous previews but the storage of
				// garbage capture data.
				previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
				                                width, height, displaySize.x,
				                                displaySize.y, largest);

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
	 * Opens the camera specified by {@link Camera2BasicFragment#cameraId}.
	 */
	private void openCamera(int width, int height)
	{
		if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
		{
			requestCameraPermission();
			return;
		}

		setupCamera(width, height);
		configureTransform(width, height);
		Activity activity = getActivity();
		CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
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

	/**
	 * Closes the current {@link CameraDevice}.
	 */
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

	/**
	 * Starts a background thread and its {@link Handler}.
	 */
	private void startBackgroundThread()
	{
		backgroundThread = new HandlerThread("Camera2 Background Thread");
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
	private void createPreviewSession()
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
			previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			previewRequestBuilder.addTarget(surface);

			// Here, we create a CameraCaptureSession for camera preview.
			cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
			                                  new CameraCaptureSession.StateCallback()
			                                  {
				                                  @Override
				                                  public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession)
				                                  {
					                                  // If the camera is not closed
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
	 * Configures the necessary {@link Matrix} transformation to `textureView`.
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
		if (currentCamera == CameraCharacteristics.LENS_FACING_BACK)
			lockFocus();
		else
			captureBurstSequence();
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
	 * Run the pre-capture sequence for capturing a still image. This method should be called when
	 * we get a response in {@link #captureCallback} from {@link #lockFocus()}.
	 */
	private void runPreCaptureSequence()
	{
		try
		{
			// This is how to tell the camera to trigger.
			previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
			                          CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
			// Tell #captureCallback to wait for the precapture sequence to be set.
			state = STATE_WAITING_PRE_CAPTURE;
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
		final Activity activity = getActivity();

		if (activity != null && cameraDevice != null)
		{
			try
			{
				// This is the CaptureRequest.Builder that we use to take a picture.
				final CaptureRequest.Builder captureBuilder =
						cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
				captureBuilder.addTarget(imageReader.getSurface());

				// Use the same AE and AF modes as the preview.
				captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
				                   CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
				captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
				                   CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

				// Orientation
				int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
				captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

				CameraCaptureSession.CaptureCallback CaptureCallback
						= new CameraCaptureSession.CaptureCallback()
				{

					@Override
					public void onCaptureCompleted(@NonNull CameraCaptureSession session,
							@NonNull CaptureRequest request,
							@NonNull TotalCaptureResult result)
					{
						unlockFocus();
					}
				};

				captureSession.stopRepeating();
				captureSession.capture(captureBuilder.build(), CaptureCallback, null);
			}
			catch (CameraAccessException e)
			{
				e.printStackTrace();
			}
		}
	}

	/**
	 * Capture a still picture. This method should be called when we get a response in
	 * {@link #captureCallback} from both {@link #lockFocus()}.
	 */
	private void captureBurstSequence()
	{
		final Activity activity = getActivity();

		if (activity != null && cameraDevice != null)
		{
			try
			{
//				CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
//				captureBuilder.addTarget(imageReader.getSurface());

				// Orientation
//				int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
//				captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

				previewRequestBuilder.addTarget(imageReader.getSurface());

				List<CaptureRequest> captureList = new ArrayList<>();
//				for (int i = 0; i < MAX_IMAGES; i++)
//					captureList.add(captureBuilder.build());
				for (int i = 0; i < MAX_IMAGES; i++)
					captureList.add(previewRequestBuilder.build());

				counter = 0;

				CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback()
				{
					@Override
					public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
							TotalCaptureResult result)
					{
						if (++counter >= MAX_IMAGES)
							if (currentCamera == CameraCharacteristics.LENS_FACING_BACK)
								unlockFocus();
							else
								restartPreview();
					}
				};

				captureSession.stopRepeating();
				captureSession.captureBurst(captureList, captureCallback, null);
				previewRequestBuilder.removeTarget(imageReader.getSurface());
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

	private void restartPreview()
	{
		try
		{
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
		switch (view.getId())
		{
			case R.id.capture:
			{
				takePicture();
				break;
			}
			case R.id.switchCam:
			{
				currentCamera = currentCamera == CameraCharacteristics.LENS_FACING_BACK ?
				                CameraCharacteristics.LENS_FACING_FRONT :
				                CameraCharacteristics.LENS_FACING_BACK;
				closeCamera();
				openCamera(textureView.getWidth(), textureView.getHeight());
				break;
			}
		}
	}

	/**
	 * Compares two {@code Size}s based on their areas.
	 */
	static class CompareSizesByArea implements Comparator<Size>
	{
		@Override
		public int compare(Size lhs, Size rhs)
		{
			// We cast here to ensure the multiplications won't overflow
			return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
					                   (long) rhs.getWidth() * rhs.getHeight());
		}
	}
}
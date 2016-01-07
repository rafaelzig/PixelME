package tsinghua.mediatech.rafaelzig.pixelme.camera.component;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import tsinghua.mediatech.rafaelzig.pixelme.AndroidMp4;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * Created by Zig on 06/01/2016.
 */
public class CameraCloser implements Runnable
{
	private static final String TAG           = CameraCloser.class.getSimpleName();
	public static final int    CAMERA_CLOSED = 5;
	public static final int    ERROR         = -3;
	private Semaphore            cameraOpenCloseLock;
	private CameraCaptureSession captureSession;
	private CameraDevice         cameraDevice;
	private ImageReader          imageReader;
	private MediaActionSound     mediaActionSound;
	private Handler              handler;

	public CameraCloser(Semaphore cameraOpenCloseLock, CameraCaptureSession captureSession, CameraDevice cameraDevice, ImageReader imageReader, MediaActionSound mediaActionSound, Handler handler)
	{
		this.cameraOpenCloseLock = cameraOpenCloseLock;
		this.captureSession = captureSession;
		this.cameraDevice = cameraDevice;
		this.imageReader = imageReader;
		this.mediaActionSound = mediaActionSound;
		this.handler = handler;
	}

	@Override
	public void run()
	{
		String errorMessage = new String();

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
			errorMessage = e.getMessage();
		}
		finally
		{
			cameraOpenCloseLock.release();

			Message message = (errorMessage.isEmpty()) ?
			                  handler.obtainMessage(CAMERA_CLOSED) :
			                  handler.obtainMessage(ERROR, errorMessage);
			message.sendToTarget();
		}
	}
}
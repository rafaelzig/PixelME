package tsinghua.mediatech.rafaelzig.pixelme;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;


/**
 * Created by omar on 12/10/15.
 */
public class PixelMeStatics {

    /**
     * This static method might not work with the implementation of full path of files because of the URI
     *
     * @param context Just pass the context from the FeedAdapter!
     * @param uri the uri of the file
     * @return will return the proper height of the image according to the width of the screen
     */
    public static int getHeightForVideoView(Context context, Uri uri){
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(context, uri);

        WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        Log.i("Test", "Width: " + metrics.widthPixels);

        Bitmap bmp = retriever.getFrameAtTime();

        return (bmp.getHeight() * metrics.widthPixels) / bmp.getWidth();
    }
}

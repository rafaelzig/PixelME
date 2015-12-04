package tsinghua.mediatech.rafaelzig.pixelme;

import java.io.File;
import java.util.Arrays;
import java.util.Vector;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import org.jcodec.api.android.SequenceEncoder;

/**
 * Created by gabri on 12/4/2015.
 */
public class JpegToMp4 {

    private File                _dir;
    private String              _videoName;
    private Vector<File>        _imgFiles;

    public JpegToMp4(String directory, String videoName, Vector<String> imagesPath) {
        _dir = new File(directory);
        _videoName = videoName;
        setImageFiles(imagesPath);
    }

    public JpegToMp4(String directory, String videoName) {
        _dir = new File(directory);
        _imgFiles = new Vector<>(Arrays.asList(_dir.listFiles()));
        _videoName = videoName;
    }

    public void setVideoName(String vname) {
        _videoName = vname;
    }

    public String getVideoName(){
        return _videoName;
    }

    public void setDir(String directory, boolean imgHere) {
        _dir = new File(directory);
        if (imgHere == true) _imgFiles = new Vector<>(Arrays.asList(_dir.listFiles()));

    }

    public File getDir() {
        return _dir;
    }

    public void setImageFiles(Vector<String> imagesPath) {
        _imgFiles.clear();
        for (int i = 0; i < imagesPath.size(); i++) {
            addImage(imagesPath.get(i));
        }
    }

    public void addImage(String imgName) {
        _imgFiles.add(new File(imgName));
    }

    /**
     * It will create a mp4 video with the images contained in the directory attribute with the fps send in parameter.
     * @param fps
     */
    public void imagesToVideo(int fps) {
         //	FOR ANDROID
         String dcim = _dir.getPath();
         System.out.println(dcim + File.separator + "video.mp4");
         try {
            System.out.println("CREATE FILE !");
            File videoFile = new File(dcim + File.separator + "video.mp4");
//                 System.out.println("FILE CREATED !");
//                 if (file.createNewFile()) System.out.println("SUCCESS !");
//                 else System.out.println("FAILURE !");
//                 System.out.println(dcim+"/video.mp4");
//                 System.out.println("SHOWN !");
            SequenceEncoder enc = new SequenceEncoder(videoFile);
            for (File f : _imgFiles)
                enc.encodeImage(BitmapFactory.decodeFile(f.getPath() + f.getName()));
            enc.finish();
         } catch (java.io.IOException e) {
            System.err.println(e.getMessage());
         }


            // FOR COMPUTER (AWT REPLACED BY ANDROID)
//            File file = new File(dcim + File.separator + "video.mp4");
//            if (file.createNewFile()) System.out.println("SUCCESS !");
//    //          SequenceEncoder enc = new SequenceEncoder(file);
//            Mp4Encoder encod = new Mp4Encoder(file);
//            BufferedImage image = ImageIO.read(new File(dcim + "/test1.jpg"));
//            BufferedImage image2 = ImageIO.read(new File(dcim + "/test2.jpg"));
//
//            for (int i = 0 ; i < 20 ; i++) {
//                encod.encodeImage(image);
//            }
//            for (int i = 0 ; i < 20 ; i++) {
//                encod.encodeImage(image2);
//            }
    //          enc.encodeImage(BitmapFactory.decodeFile(dcim+ "/test1.jpg"));
    //          enc.encodeImage(BitmapFactory.decodeFile(dcim + "/test2.jpg"));
//            encod.finish();
//            System.out.println("DONE !");
//        } catch (java.io.IOException e) {
//            System.out.println("ERROR !");
//            System.out.println(e.getMessage());
    }

}

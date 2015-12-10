package tsinghua.mediatech.rafaelzig.pixelme;

import java.io.File;
import java.util.Arrays;
import java.util.Vector;

import android.os.Environment;

import java.io.File;
import java.util.Arrays;
import java.util.Vector;

/**
 * Created by gabri on 12/10/2015.
 */
public class JpegToMp4 {
    private File _dir = null;
    private String              _videoName = null;
    private Vector<File> _imgFiles;
    private File        _outDir;
    /**
     *
     * The contructor with the precise images path.
     *
     * @param directory the images and output directory.
     * @param videoName the video name.
     * @param imagesPath the path of all the pictures.
     */
    public JpegToMp4(String directory, String videoName, Vector<String> imagesPath) {
        _dir = new File(directory);
        _videoName = videoName;
        setImageFiles(imagesPath);
    }

    /**
     *
     * The constructor with variable images path.
     *
     * @param videoName the output video name.
     * @param imagesPath the images paths.
     */
    public JpegToMp4(String videoName, Vector<String> imagesPath) {
        _videoName = videoName;
        setImageFiles(imagesPath);
    }

    /**
     *
     * The contructor with the directory containing only the images.
     *
     * @param directory the pictures directory.
     * @param videoName the video name.
     */
    public JpegToMp4(String directory, String videoName) {
        _dir = new File(directory);
        _imgFiles = new Vector<>(Arrays.asList(_dir.listFiles()));
        _videoName = videoName;
    }

    public JpegToMp4(File outDir, String directory, String videoName) {
        _dir = new File(directory);
        _imgFiles = new Vector<>(Arrays.asList(_dir.listFiles()));
        _videoName = videoName;
        _outDir = outDir;
    }

    public void setVideoName(String videoName) {
        _videoName = videoName;
    }

    public String getVideoName(){
        return _videoName;
    }

    public void setDir(String directory, boolean imgHere) {
        _dir = new File(directory);
        if (imgHere) _imgFiles = new Vector<>(Arrays.asList(_dir.listFiles()));
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
        _imgFiles.add(new File((_dir != null ? _dir + File.separator + imgName : imgName)));
    }

    /**
     * It will create a mp4 video with the images contained in the directory attribute with the fps send in parameter.
     * @param fps the frame per second (I advise you to not send less than 6).
     */
    public void imagesToVideo(int fps) {
        String dir = "";
        if (_dir != null)
            dir = _dir.getPath();
        String vName = _videoName;
        System.out.println(vName);
        try {
            File videoFile = new File(_outDir + File.separator + vName);
            System.out.println(videoFile.toString());
            AndroidMp4 enc = new AndroidMp4(videoFile, fps);
            for (File f : _imgFiles)
                enc.encodeImage(f);
            enc.finish();
        } catch (java.io.IOException e) {
            System.err.println(e.getMessage());
        }
    }
}

package tsinghua.mediatech.rafaelzig.pixelme;

/**
 * Created by gabri on 12/10/2015.
 */
import java.io.File;
import java.io.IOException;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import android.util.Log;
import org.jcodec.common.NIOUtils;
import org.jcodec.common.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Size;
import org.jcodec.containers.mp4.Brand;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.containers.mp4.TrackType;
import org.jcodec.containers.mp4.muxer.FramesMP4MuxerTrack;
import org.jcodec.containers.mp4.muxer.MP4Muxer;
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.scale.BitmapUtil;
import org.jcodec.scale.ColorUtil;
import org.jcodec.scale.Transform;
import org.jcodec.common.model.Picture;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 *
 * @author The JCodec project
 *
 */
public class AndroidMp4 {
    private static final String TAG = "AndroidMp4 EncoderHelper" ;
    private SeekableByteChannel   ch;
    private int                   frameNo;
    private MP4Muxer              mux;
    private Size                  size;
    private ByteBuffer            sps;
    private ByteBuffer            pps;
    private ArrayList<ByteBuffer> spsList;
    private ArrayList<ByteBuffer> ppsList;
    private FramesMP4MuxerTrack   outTrack;
    private ByteBuffer            _out;
    private H264Encoder           encoder;
    private MP4Muxer              muxer;
    private Transform             transform;
    private Picture               toEncode;

    public AndroidMp4(File out, int fps) throws IOException
    {
        this.ch = NIOUtils.writableFileChannel(out);

        // Muxer that will store the encoded frames
        muxer = new MP4Muxer(ch, Brand.MP4);

        // Add video track to muxer
        outTrack = muxer.addTrack(TrackType.VIDEO, fps);

        // Allocate a buffer big enough to hold output frames
        _out = ByteBuffer.allocate(1920 * 1080 * 2);

        // Create an instance of encoder
        encoder = new H264Encoder();

        // Transform to convert between RGB and YUV
        transform = ColorUtil.getTransform(ColorSpace.RGB, encoder.getSupportedColorSpaces()[0]);

        // EncoderHelper extra data ( SPS, PPS ) to be stored in a special place of
        // MP4
        spsList = new ArrayList<>();
        ppsList = new ArrayList<>();
//        this.ch = NIOUtils.writableFileChannel(out);
//
//        mux = new MP4Muxer(ch, Brand.MP4);
//
//        outTrack = mux.addTrack(TrackType.VIDEO, fps);
    }

    public void encodeImage(File jpeg) throws IOException
    {
        Bitmap read = ImageUtils.decodeAndTransform(jpeg); // Should be done on ASyncTask if possible
        Picture pic = BitmapUtil.fromBitmap(read);
        if (toEncode == null) {
            toEncode = Picture.create(pic.getWidth(), pic.getHeight(), encoder.getSupportedColorSpaces()[0]);
        }

        // Perform conversion
        transform.transform(pic, toEncode);

        // Encode image into H.264 frame, the result is stored in '_out' buffer
        _out.clear();
        ByteBuffer result = encoder.encodeFrame(toEncode, _out);

        // Based on the frame above form correct MP4 packet
        spsList.clear();
        ppsList.clear();
        H264Utils.wipePS(result, spsList, ppsList);
//		        NALUnit nu = NALUnit.read(NIOUtils.from(result.duplicate(), FPS));
        H264Utils.encodeMOVPacket(result);

        // We presume there will be only one SPS/PPS pair for now
        if (sps == null && spsList.size() != 0)
            sps = spsList.get(0);
        if (pps == null && ppsList.size() != 0)
            pps = ppsList.get(0);

        // Add packet to video track
        outTrack.addFrame(new MP4Packet(result, frameNo, 1, 1, frameNo, true, null,
                frameNo, 0));

        frameNo++;
//        if (size == null) {
//            Bitmap read = BitmapFactory.decodeFile(jpeg.getAbsolutePath());
//            size = new Size(read.getWidth(), read.getHeight());
//        }
//        // Add packet to video track
//        outTrack.addFrame(new MP4Packet(NIOUtils.fetchFrom(jpeg), frameNo, 1, 1, frameNo, true, null, frameNo, 0));
//
//        frameNo++;


    }

    public void finish() throws IOException {
        outTrack.addSampleEntry(H264Utils.createMOVSampleEntry(spsList, ppsList, 0));

        // Write MP4 header and finalize recording
        muxer.writeHeader();
        NIOUtils.closeQuietly(ch);
        Log.d(TAG, "FINISHED !");
    }
}
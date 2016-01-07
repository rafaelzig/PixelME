package edge;

import java.awt.Image;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class edge {

	public static void edgePicture(String inName, String outName) throws IOException {
			  int GX[][] = {
			    {-1, 0, 1},
			    {-2 ,0, 2},
			    {-1 , 0, 1}
			  };
			  int GY[][] = {
			    {1, 2, 1},
			    {0 ,0, 0},
			    {-1 , -2, -1}
			  };

			  BufferedImage img = ImageIO.read(new File(inName));
			  
			  ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);  
			  ColorConvertOp op = new ColorConvertOp(cs, null);
			  BufferedImage gray = op.filter(img, null);

			  byte[] bits = ((DataBufferByte)gray.getRaster().getDataBuffer()).getData();
			  byte[] out = new byte[gray.getHeight() * gray.getWidth()];
			  int cols = img.getWidth();
			  int rows = img.getHeight();

			  int SUM = 0;
			  for(int Y=0; Y<=(rows-1); Y++)  {
			       for(int X=0; X<=(cols-1); X++)  {
			            int sumX = 0;
			            int sumY = 0;

				    if(Y==0 || Y==rows-1)
					 SUM = 0;
				    else if(X==0 || X==cols-1)
					 SUM = 0;

				    else   {
				      for(int I=-1; I<=1; I++)  {
						  for(int J=-1; J<=1; J++)  {
						      int val = X + I + (Y + J)*cols;
						      byte bit = bits[val];
						     sumX = sumX + (int)(bit  * GX[I+1][J+1]);
						  }
				      }

				      for(int I=-1; I<=1; I++)  {
						  for(int J=-1; J<=1; J++)  {
						      sumY = sumY + (int)( bits[X + I +
							     (Y + J)*cols] * GY[I+1][J+1]);
						  }
				      }

			              SUM = abs(sumX) + abs(sumY);
			            }

			            if (SUM>255) SUM=255;
			            if (SUM<0) SUM=0;

			            int val = X + Y*cols;
			            out[val] = (byte) (255 - (byte)(SUM));
			         }
			  }
			  
		 
				try {
					BufferedImage bufferedImage = getGrayscale(cols, out);
			        File outputfile = new File(outName);
			        ImageIO.write(bufferedImage, "jpg", outputfile);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		 

	}
	
	public static BufferedImage getGrayscale(int width, byte[] buffer) {
	    int height = buffer.length / width;
	    ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
	    int[] nBits = { 8 };
	    ColorModel cm = new ComponentColorModel(cs, nBits, false, true,
	            Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
	    SampleModel sm = cm.createCompatibleSampleModel(width, height);
	    DataBufferByte db = new DataBufferByte(buffer, width * height);
	    WritableRaster raster = Raster.createWritableRaster(sm, db, null);
	    BufferedImage result = new BufferedImage(cm, raster, false, null);

	    return result;
	}
	
	private static int abs(int sumX) {
		return (sumX > 0 ? sumX : -sumX) ;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		try {
			edgePicture("test.jpg", "out.jpg");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}

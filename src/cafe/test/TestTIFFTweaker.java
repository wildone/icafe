package cafe.test;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cafe.image.ImageColorType;
import cafe.image.ImageFrame;
import cafe.image.ImageParam;
import cafe.image.meta.exif.Exif;
import cafe.image.meta.exif.ExifTag;
import cafe.image.meta.exif.TiffExif;
import cafe.image.options.TIFFOptions;
import cafe.image.tiff.FieldType;
import cafe.image.tiff.TIFFTweaker;
import cafe.image.tiff.TiffFieldEnum.Compression;
import cafe.image.tiff.IFD;
import cafe.image.writer.TIFFWriter;
import cafe.io.FileCacheRandomAccessInputStream;
import cafe.io.FileCacheRandomAccessOutputStream;
import cafe.io.RandomAccessInputStream;
import cafe.io.RandomAccessOutputStream;
import cafe.util.FileUtils;

public class TestTIFFTweaker {

	public static void main(String[] args) throws Exception {
		RandomAccessInputStream rin = new FileCacheRandomAccessInputStream(new FileInputStream(args[0]));
		RandomAccessOutputStream rout = null;
		
		if(args.length > 1) {			
			if(args[1].equalsIgnoreCase("copycat")) {
				rout = new FileCacheRandomAccessOutputStream(new FileOutputStream("NEW.tif"));
				TIFFTweaker.copyCat(rin, rout);
				rout.close();
			} else if(args[1].equalsIgnoreCase("snoop")) {
				TIFFTweaker.readMetadata(rin);
			} else if(args[1].equalsIgnoreCase("extractThumbnail")){
				TIFFTweaker.extractThumbnail(rin, "thumbnail");					
			} else if(args[1].equalsIgnoreCase("extractICCProfile")) {
				byte[] icc_profile = TIFFTweaker.extractICCProfile(rin);
				if(icc_profile != null) {
					OutputStream iccOut = new FileOutputStream(new File("ICCProfile.icc"));
					iccOut.write(icc_profile);
					iccOut.close();
				}
			} else if(args[1].equalsIgnoreCase("retainpage")) {
				int pageCount = TIFFTweaker.getPageCount(rin);
				rout = new FileCacheRandomAccessOutputStream(new FileOutputStream("NEW.tif"));
				if(pageCount > 1)
					TIFFTweaker.retainPages(rin, rout, pageCount - 1); // Last page
				else
					TIFFTweaker.copyCat(rin, rout);
				rout.close();
			} else if(args[1].equalsIgnoreCase("writemultipage") || args[1].equalsIgnoreCase("insertpage")) {
				File[] files = FileUtils.listFilesMatching(new File(args[2]), args[3]);
				ImageFrame[] frames = new ImageFrame[files.length];				
				for(int i = 0; i < files.length; i++) {
					FileInputStream fin = new FileInputStream(files[i]);
					BufferedImage image = javax.imageio.ImageIO.read(fin);
					frames[i] = new ImageFrame(image);
					fin.close();
				}				
				
				ImageParam.ImageParamBuilder builder = new ImageParam.ImageParamBuilder();
				  
				TIFFOptions tiffOptions = new TIFFOptions();
				tiffOptions.setTiffCompression(Compression.LZW);
				tiffOptions.setApplyPredictor(true);
				tiffOptions.setDeflateCompressionLevel(6);
				builder.imageOptions(tiffOptions);
				
				frames[0].setFrameMeta(builder.colorType(ImageColorType.GRAY_SCALE).applyDither(true).ditherThreshold(18).hasAlpha(true).build());
				
				tiffOptions = new TIFFOptions(tiffOptions); // Copy constructor		
				tiffOptions.setTiffCompression(Compression.DEFLATE);
								
				frames[1].setFrameMeta(builder.imageOptions(tiffOptions).build());
				
				tiffOptions = new TIFFOptions(tiffOptions);				
				tiffOptions.setTiffCompression(Compression.CCITTFAX4);
				
				ImageParam meta = builder.colorType(ImageColorType.BILEVEL).ditherThreshold(50).imageOptions(tiffOptions).build();
				
				for(int i = 2; i < frames.length; i++)
					frames[i].setFrameMeta(meta);
				
				rout = new FileCacheRandomAccessOutputStream(new FileOutputStream("NEW.tif"));
				
				if(args[1].equalsIgnoreCase("writemultipage"))
					TIFFTweaker.writeMultipageTIFF(rout, frames);
				else {
					// This one line test one time insert using insertPages
					//TIFFTweaker.insertPages(rin, rout, 0, frames);
					// The following lines test insert pages each at a time
					long t1 = System.currentTimeMillis();
					List<IFD> list = new ArrayList<IFD>();
					int offset = TIFFTweaker.prepareForInsert(rin, rout, list);
					int index = 3;
					TIFFWriter writer = new TIFFWriter();
					writer.setImageParam(frames[0].getFrameParam());
					for(int i = 0; i < frames.length; i++) {
						offset = TIFFTweaker.insertPage(frames[i].getFrame(), index+=2, rout, list, offset, writer);
					}
					int nColors = 2;
					byte[] reds   = new byte[]{0,(byte)255};
					byte[] greens = new byte[]{0,(byte)255};
					byte[] blues  = new byte[]{0,(byte)255};
					int width = 400; // Dimensions of the image
					int height = 400;
					// Let's create a IndexColorModel for this image.
					IndexColorModel colorModel = new IndexColorModel(1, nColors, reds, greens, blues);
					// Let's create a BufferedImage for an indexed color image.
					BufferedImage im = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY, colorModel);
					// We need its raster to set the pixels' values.
					WritableRaster raster = im.getRaster();
					// Put the pixels on the raster. Note that only values 0 and 1 are used for the pixels.
					for(int h = 0; h < height; h++)
						for(int w = 0; w < width; w++)
							if (((h/50)+(w/50)) % 2 == 0) raster.setSample(w, h, 0, 0); // checkerboard pattern.
							else raster.setSample(w, h, 0, 1);
					writer.setImageParam(frames[2].getFrameParam());
					TIFFTweaker.insertPage(im, 0, rout, list, offset, writer);
					TIFFTweaker.finishInsert(rout, list);
					long t2 = System.currentTimeMillis();
					System.out.println("time used: " + (t2-t1) + "ms");
				}
				rout.close();
			} else if(args[1].equalsIgnoreCase("splitpage")) {
				TIFFTweaker.splitPages(rin, FileUtils.getNameWithoutExtension(new File(args[0])));
			} else if(args[1].equalsIgnoreCase("insertexif")) {
				rout = new FileCacheRandomAccessOutputStream(new FileOutputStream("EXIF.tif"));
				TIFFTweaker.insertExif(rin, rout, populateExif(), true);
				rout.close();
			} else if(args[1].equalsIgnoreCase("removepage")) {
				int pageCount = TIFFTweaker.getPageCount(rin);
				rout = new FileCacheRandomAccessOutputStream(new FileOutputStream("NEW.tif"));
				if(pageCount > 1)
					TIFFTweaker.removePages(rin, rout, 0, 1, 1, 1, 5, 5, 4, 100, -100);
				else
					TIFFTweaker.copyCat(rin, rout);
				rout.close();
			}
		}
		
		rin.close();
	}
	
	// This method is for testing only
	private static Exif populateExif() throws Exception {
		// Create an EXIF wrapper
		Exif exif = new TiffExif();		
		DateFormat formatter = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
		exif.addExifField(ExifTag.EXPOSURE_TIME, FieldType.RATIONAL, new int[] {10, 600});
		exif.addExifField(ExifTag.FNUMBER, FieldType.RATIONAL, new int[] {49, 10});
		exif.addExifField(ExifTag.ISO_SPEED_RATINGS, FieldType.SHORT, new short[]{273});
		//All four bytes should be interpreted as ASCII values - represents [0220]
		exif.addExifField(ExifTag.EXIF_VERSION, FieldType.UNDEFINED, new byte[]{48, 50, 50, 48});
		exif.addExifField(ExifTag.DATE_TIME_ORIGINAL, FieldType.ASCII, formatter.format(new Date()));
		exif.addExifField(ExifTag.DATE_TIME_DIGITIZED, FieldType.ASCII, formatter.format(new Date()));
		exif.addExifField(ExifTag.FOCAL_LENGTH, FieldType.RATIONAL, new int[] {240, 10});
		
		return exif;
	}
}
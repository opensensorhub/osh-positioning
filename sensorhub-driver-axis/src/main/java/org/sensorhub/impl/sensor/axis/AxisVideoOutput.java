/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
The Initial Developer is Botts Innovative Research Inc. Portions created by the Initial
Developer are Copyright (C) 2014 the Initial Developer. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.axis;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.ByteOrder;
import javax.media.Buffer;
import net.opengis.swe.v20.BinaryBlock;
import net.opengis.swe.v20.BinaryComponent;
import net.opengis.swe.v20.BinaryEncoding;
import net.opengis.swe.v20.ByteEncoding;
import net.opengis.swe.v20.DataArray;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataRecord;
import net.opengis.swe.v20.DataType;
import net.opengis.swe.v20.Time;
import net.sf.jipcam.axis.media.protocol.http.MjpegStream;
import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.vast.data.CountImpl;
import org.vast.data.DataBlockMixed;
import org.vast.data.SWEFactory;
import org.vast.swe.SWEConstants;
import org.vast.swe.SWEHelper;


/**
 * <p>
 * Implementation of sensor interface for generic Axis Cameras using IP
 * protocol. This particular class provides time-tagged video output from the video
 * camera capabilities.
 * </p>
 *
 * <p>
 * Copyright (c) 2014
 * </p>
 * 
 * @author Mike Botts <mike.botts@botts-inc.com>
 * @since October 30, 2014
 */

// TODO Need a separate class for outputting camera settings (e.g. imageSize, brightness, iris, IR, focus, imageRotation)

public class AxisVideoOutput extends AbstractSensorOutput<AxisCameraDriver>
{
	DataComponent videoDataStruct;
	BinaryEncoding videoEncoding;
	boolean reconnect;
	boolean streaming;
	
	
	public AxisVideoOutput(AxisCameraDriver driver)
    {
    	super("videoOutput", driver);   	
    }
    
    
    protected void init() throws SensorException
    {
		try
		{
			// get image size from camera HTTP interface
			int[] imgSize = getImageSize();
			SWEFactory fac = new SWEFactory();
			
			// build output structure
			videoDataStruct = fac.newDataRecord(2);
			videoDataStruct.setName(getName());
			
			Time time = fac.newTime();
			time.getUom().setHref(Time.ISO_TIME_UNIT);
			time.setDefinition(SWEConstants.DEF_SAMPLING_TIME);
			videoDataStruct.addComponent("time", time);
					
			DataArray img = fac.newDataArray(imgSize[1]);
			img.setDefinition("http://sensorml.com/ont/swe/property/VideoFrame");
			videoDataStruct.addComponent("videoFrame", img);
			
			DataArray imgRow = fac.newDataArray(imgSize[0]);
			img.addComponent("row", imgRow);
			
			DataRecord imgPixel = fac.newDataRecord(3);
			imgPixel.addComponent("red", new CountImpl(DataType.BYTE));
			imgPixel.addComponent("green", new CountImpl(DataType.BYTE));
			imgPixel.addComponent("blue", new CountImpl(DataType.BYTE));
			imgRow.addComponent("pixel", imgPixel);
			
			// video encoding
			videoEncoding = fac.newBinaryEncoding();
			videoEncoding.setByteEncoding(ByteEncoding.RAW);
			videoEncoding.setByteOrder(ByteOrder.BIG_ENDIAN);
            BinaryComponent timeEnc = fac.newBinaryComponent();
            timeEnc.setRef("/" + time.getName());
            timeEnc.setCdmDataType(DataType.DOUBLE);
            videoEncoding.addMemberAsComponent(timeEnc);
            BinaryBlock compressedBlock = fac.newBinaryBlock();
            compressedBlock.setRef("/" + img.getName());
            compressedBlock.setCompression("JPEG");
            videoEncoding.addMemberAsBlock(compressedBlock);
            
            // resolve encoding so compressed blocks can be properly generated
            SWEHelper.assignBinaryEncoding(videoDataStruct, videoEncoding);
			
		}
		catch (Exception e)
		{
			throw new SensorException("Error while initializing video output", e);
		}
		
		startStream();
    }
	
	
	protected int[] getImageSize() throws IOException
	{
		String ipAddress = parentSensor.getConfiguration().ipAddress;
		URL getImgSizeUrl = new URL("http://" + ipAddress + "/axis-cgi/view/imagesize.cgi?camera=1");
		BufferedReader reader = new BufferedReader(new InputStreamReader(getImgSizeUrl.openStream()));
		
		int imgSize[] = new int[2];
		String line;
		while ((line = reader.readLine()) != null)
		{
			// split line and parse each possible property
			String[] tokens = line.split("=");
			if (tokens[0].trim().equalsIgnoreCase("image width"))
				imgSize[0] = Integer.parseInt(tokens[1].trim());
			else if (tokens[0].trim().equalsIgnoreCase("image height"))
				imgSize[1] = Integer.parseInt(tokens[1].trim());
		}
		
		// index 0 is width, index 1 is height
		return imgSize;
	}
	
	
	protected void startStream()
	{
		try
		{
			String ipAddress = parentSensor.getConfiguration().ipAddress;
			final URL videoUrl = new URL("http://" + ipAddress + "/mjpg/video.mjpg");
			reconnect = true;
			
			Thread t = new Thread(new Runnable()
	    	{
				@Override
				public void run()
				{
					while (reconnect)
					{
						// send http query
						try
						{
							InputStream is = new BufferedInputStream(videoUrl.openStream());
							MjpegStream stream = new MjpegStream(is, null);
							streaming = true;
							
							while (streaming)
							{
								// extract next frame from MJPEG stream
								Buffer buf = new Buffer();
						        buf.setData(new byte[]{});
						        stream.read(buf);
						        byte[] frameData = (byte[]) buf.getData();
						        
						        // create new data block
						        DataBlock dataBlock;
						        if (latestRecord == null)
                                    dataBlock = videoDataStruct.createDataBlock();
                                else
                                    dataBlock = latestRecord.renew();
                                
						        //double timestamp = AXISJpegHeaderReader.getTimestamp(frameData) / 1000.;
                                double timestamp = System.currentTimeMillis() / 1000.;
                                dataBlock.setDoubleValue(0, timestamp);
                                //System.out.println(new DateTimeFormat().formatIso(timestamp, 0));
                                
						        // uncompress to RGB bufferd image
						        /*InputStream imageStream = new ByteArrayInputStream(frameData);						        
						        ImageInputStream input = ImageIO.createImageInputStream(imageStream); 
						        Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType("image/jpeg");
						        ImageReader reader = readers.next();
						        reader.setInput(input);
						        //int width = reader.getWidth(0);
						        //int height = reader.getHeight(0);
						        
						        // The ImageTypeSpecifier object gives you access to more info such as 
						        // bands, color model, etc.
						        //ImageTypeSpecifier imageType = reader.getRawImageType(0);
						        
						        BufferedImage rgbImage = reader.read(0);
						        byte[] byteData = ((DataBufferByte)rgbImage.getRaster().getDataBuffer()).getData();
						        ((DataBlockMixed)dataBlock).getUnderlyingObject()[1].setUnderlyingObject(byteData);*/
						        
						        // assign compressed data
						        ((DataBlockMixed)dataBlock).getUnderlyingObject()[1].setUnderlyingObject(frameData);
								
						        latestRecord = dataBlock;
		                        latestRecordTime = System.currentTimeMillis();
								eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, AxisVideoOutput.this, latestRecord));
							}
							
							// wait 1s before trying to reconnect
							Thread.sleep(1000);
						}
						catch (Exception e)
						{
							e.printStackTrace();
						}
					};
	 			}    		
	    	});
			
			t.start();
		}
    	catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	
	@Override
	public double getAverageSamplingPeriod()
	{
		return 1/30.;
	}
	

	@Override
	public DataComponent getRecordDescription()
	{
		return videoDataStruct;
	}
	

	@Override
	public DataEncoding getRecommendedEncoding()
	{
		return videoEncoding;
	}


	public void stop()
	{
				
	}

}

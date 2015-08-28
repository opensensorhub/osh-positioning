/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
The Initial Developer is Sensia Software LLC. Portions created by the Initial
Developer are Copyright (C) 2014 the Initial Developer. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.test.impl.sensor.axis;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.JFrame;
import net.opengis.sensorml.v20.AbstractProcess;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sensorhub.api.common.Event;
import org.sensorhub.api.common.IEventListener;
import org.sensorhub.api.sensor.ISensorControlInterface;
import org.sensorhub.api.sensor.ISensorDataInterface;
import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.impl.sensor.axis.AxisCameraConfig;
import org.sensorhub.impl.sensor.axis.AxisCameraDriver;
import org.sensorhub.impl.sensor.axis.AxisSettingsOutput;
import org.sensorhub.impl.sensor.axis.AxisVideoOutput;
import org.vast.data.DataChoiceImpl;
import org.vast.sensorML.SMLUtils;
import org.vast.swe.SWEUtils;
import static org.junit.Assert.*;


/**
 * <p>
 * Implementation of sensor interface for generic Axis Cameras using IP
 * protocol
 * </p>
 *
 * <p>
 * Copyright (c) 2014
 * </p>
 * 
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 */


public class TestAxisCameraDriver implements IEventListener
{
    final static int MAX_FRAMES = 3000;
	AxisCameraDriver driver;
    AxisCameraConfig config;
    int actualWidth, actualHeight;
    int dataBlockSize;
    JFrame videoWindow;
    BufferedImage img;
    int frameCount;
    
    
    @Before
    public void init() throws Exception
    {
        config = new AxisCameraConfig();
        //config.ipAddress = "root:more4less@192.168.1.50";
        //config.ipAddress = "192.168.1.50";
        //config.ipAddress = "192.168.1.60";
        config.ipAddress = "bottsgeo.simple-url.com:80";
        config.id = UUID.randomUUID().toString();
        
        driver = new AxisCameraDriver();
        driver.init(config);
        driver.start();
    }
    
    
    @Test
    public void testGetOutputDesc() throws Exception
    {
        for (ISensorDataInterface di: driver.getObservationOutputs().values())
        {
            DataComponent dataMsg = di.getRecordDescription();
            new SWEUtils(SWEUtils.V2_0).writeComponent(System.out, dataMsg, false, true);
        }
    }
    
    
    @Test
    public void testGetCommandDesc() throws Exception
    {
        for (ISensorControlInterface ci: driver.getCommandInputs().values())
        {
            DataComponent commandMsg = ci.getCommandDescription();
            new SWEUtils(SWEUtils.V2_0).writeComponent(System.out, commandMsg, false, true);
        }
    }
    
    
    @Test
    public void testGetSensorDesc() throws Exception
    {
        AbstractProcess smlDesc = driver.getCurrentDescription();
        new SMLUtils(SMLUtils.V2_0).writeProcess(System.out, smlDesc, true);
    }
    
    
    private void initWindow() throws Exception
    {
    	// prepare frame and buffered image
    	ISensorDataInterface di = driver.getObservationOutputs().get("videoOutput");
        int height = di.getRecordDescription().getComponent(1).getComponentCount();
        int width = di.getRecordDescription().getComponent(1).getComponent(0).getComponentCount();
        videoWindow = new JFrame("Video");
        videoWindow.setSize(width, height);
        videoWindow.setVisible(true);
        img = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
    }
    
    
    @Test
    public void testVideoCapture() throws Exception
    {
        initWindow();
    	
    	// register listener on data interface
        ISensorDataInterface di = driver.getObservationOutputs().get("videoOutput");
    	di.registerListener(this);
    	
        
        // start capture and wait until we receive the first frame
        synchronized (this)
        {
            while (frameCount < MAX_FRAMES)
            	this.wait();
            driver.stop();
        }
        
        assertEquals("Wrong image width", 704, actualWidth);
        assertEquals("Wrong image height", 480, actualHeight);
        assertEquals("Wrong data size", 704*480*3 + 1, dataBlockSize); // size of datablock is image+timestamp
    }
    
    
    @Test
    public void testPTZSettingsOutput() throws Exception
    {
        // register listener on data interface
        ISensorDataInterface di = driver.getObservationOutputs().get("ptzOutput");
        di.registerListener(this);
        
        // start capture and wait until we receive the first frame
        synchronized (this)
        {
            this.wait();
            driver.stop();
        }
        
        
    }
    
    
    @Test
    public void testSendPTZCommand() throws Exception
    {
        initWindow();
    	
    	// register listeners
    	ISensorDataInterface di = driver.getObservationOutputs().get("ptzOutput");
        di.registerListener(this);
        ISensorDataInterface di2 = driver.getObservationOutputs().get("videoOutput");
        di2.registerListener(this);
        
        // get ptz control interface
        ISensorControlInterface ci = driver.getCommandInputs().get("ptzControl");
        DataComponent commandDesc = ci.getCommandDescription().copy();
        
        // start capture and send commands
        synchronized (this)
        {
        	float pan = 190.0f;
        	float tilt = 0.0f;
        	int zoom = 0;
        	DataBlock commandData;
        	
        	// NOTE: uncomment one section at a time to test different parameters
        	
        	// test absolute pan
//        	while (frameCount < MAX_FRAMES)  //
//        	{
//        		if (frameCount % 30 == 0)
//        		{
//        			((DataChoiceImpl)commandDesc).setSelectedItem("pan");
//        			commandData = commandDesc.createDataBlock();
//        			pan += 5.;
//        			if (pan > 180.)
//        				pan -= 360;
//        			commandData.setFloatValue(1, pan);
//        			ci.execCommand(commandData);
//        		}                               
//        		this.wait();
//        	}
        	
        	// test absolute tilt
//        	while (frameCount < MAX_FRAMES)
//        	{
//        		if (frameCount % 30 == 0)
//        		{
//        			((DataChoiceImpl)commandDesc).setSelectedItem("tilt");
//        			commandData = commandDesc.createDataBlock();       			
//        			tilt -= 2.;
//        			if (tilt > 180.)
//        				tilt -= 180.;
//        			commandData.setFloatValue(1, tilt);
//        			ci.execCommand(commandData);
//        		}                               
//        		this.wait();
//        	}
        	
        	
        	// test absolute zoom
//        	while (frameCount < MAX_FRAMES)
//        	{
//        		if (frameCount % 30 == 0)
//        		{
//        			((DataChoiceImpl)commandDesc).setSelectedItem("zoom");
//        			commandData = commandDesc.createDataBlock();       			
//        			zoom += 100;
//        			commandData.setFloatValue(1, zoom);
//        			ci.execCommand(commandData);
//        		}                                
//        		this.wait();
//        	}
        	
        	// test relative pan
        	while (frameCount < MAX_FRAMES)  //
        	{
        		if (frameCount % 10 == 0)
        		{
        			((DataChoiceImpl)commandDesc).setSelectedItem("rpan");
        			commandData = commandDesc.createDataBlock();
        			commandData.setFloatValue(1, 5.0f);
        			ci.execCommand(commandData);
        		}                               
        		this.wait();
        	}
       	
        	// test relative tilt
//        	while (frameCount < MAX_FRAMES)  //
//        	{
//        		if (frameCount % 30 == 0)
//        		{
//        			((DataChoiceImpl)commandDesc).setSelectedItem("rtilt");
//        			commandData = commandDesc.createDataBlock();
//        			commandData.setFloatValue(1, 5.0f);
//        			ci.execCommand(commandData);
//        		}                               
//        		this.wait();
//        	}

        	// test relative zoom
//        	while (frameCount < MAX_FRAMES)  //
//        	{
//        		if (frameCount % 30 == 0)
//        		{
//        			((DataChoiceImpl)commandDesc).setSelectedItem("rzoom");
//        			commandData = commandDesc.createDataBlock();
//        			commandData.setFloatValue(1, 100.0f);
//        			ci.execCommand(commandData);
//        		}                               
//        		this.wait();
//        	}

        	driver.stop();
        }
    }
    
    
    @Override
    public void handleEvent(Event<?> e)
    {
        assertTrue(e instanceof SensorDataEvent);
        SensorDataEvent newDataEvent = (SensorDataEvent)e;
        
        if (newDataEvent.getSource().getClass().equals(AxisVideoOutput.class))
        {
	        DataComponent camDataStruct = newDataEvent.getRecordDescription().copy();
	        camDataStruct.setData(newDataEvent.getRecords()[0]);
	        
	        actualHeight = camDataStruct.getComponent(1).getComponentCount();
	        actualWidth = camDataStruct.getComponent(1).getComponent(0).getComponentCount();
	        dataBlockSize = newDataEvent.getRecords()[0].getAtomCount();
	        		
	        byte[] frameData = (byte[])camDataStruct.getComponent(1).getData().getUnderlyingObject();
                        
	        /*// use RGB data directly
	        byte[] destArray = ((DataBufferByte)img.getRaster().getDataBuffer()).getData();
	        System.arraycopy(frameData, 0, destArray, 0, dataBlockSize-1);*/
	        
	        // uncompress JPEG data
	        BufferedImage rgbImage;
            try
            {
                InputStream imageStream = new ByteArrayInputStream(frameData);                               
                ImageInputStream input = ImageIO.createImageInputStream(imageStream); 
                Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType("image/jpeg");
                ImageReader reader = readers.next();
                reader.setInput(input);
                rgbImage = reader.read(0);
                videoWindow.getContentPane().getGraphics().drawImage(rgbImage, 0, 0, null);
            }
            catch (IOException e1)
            {
                throw new RuntimeException(e1);
            }
            
            frameCount++;
        }
        else if (newDataEvent.getSource().getClass().equals(AxisSettingsOutput.class))
        {
        	DataComponent ptzParams = newDataEvent.getRecordDescription().copy();
        	ptzParams.setData(newDataEvent.getRecords()[0]);
        	System.out.println(ptzParams);
        }
        
        synchronized (this) { this.notify(); }
    }
    
    
    @After
    public void cleanup()
    {
        driver.stop();
    }
}

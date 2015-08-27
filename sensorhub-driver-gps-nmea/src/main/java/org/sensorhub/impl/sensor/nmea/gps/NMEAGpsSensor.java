/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.nmea.gps;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import org.sensorhub.api.comm.CommConfig;
import org.sensorhub.api.comm.ICommProvider;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.sensor.ISensorDataInterface;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * <p>
 * Driver implementation for NMEA 0183 compatible GPS units
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Aug 27, 2015
 */
public class NMEAGpsSensor extends AbstractSensorModule<NMEAGpsConfig>
{
    static final Logger log = LoggerFactory.getLogger(NMEAGpsSensor.class);
    
    public static final String GLL_MSG = "GLL";
    public static final String GGA_MSG = "GGA";
    public static final String GSA_MSG = "GSA";
    public static final String RMC_MSG = "RMC";
    public static final String VTG_MSG = "VTG";
    public static final String ZDA_MSG = "ZDA";
    
    ICommProvider<? super CommConfig> commProvider;
    BufferedReader reader;
    volatile boolean started;
    
    double lastFixUtcTime;
    
    
    public NMEAGpsSensor()
    {        
    }
    
    
    @Override
    public void init(NMEAGpsConfig config) throws SensorHubException
    {
        super.init(config);
        
        // create outputs depending on selected sentences
        if (config.activeSentences.contains(GLL_MSG) || config.activeSentences.contains(GGA_MSG))
        {
            LLALocationOutput dataInterface = new LLALocationOutput(this);
            addOutput(dataInterface, false);
            dataInterface.init();
        }
        
    }
    
    
    @Override
    protected void updateSensorDescription()
    {
        synchronized (sensorDescription)
        {
            super.updateSensorDescription();
            sensorDescription.setId("GPS_SENSOR");
            sensorDescription.setDescription("NMEA 0183 Compatible GNSS Receiver");
        }
    }


    @Override
    public void start() throws SensorHubException
    {
        if (started)
            return;        
                
        // init comm provider
        if (commProvider == null)
        {
            // we need to recreate comm provider here because it can be changed by UI
            try
            {
                if (config.commSettings == null)
                    throw new SensorHubException("No communication settings specified");
                
                commProvider = config.commSettings.getProvider();
                commProvider.start();
            }
            catch (Exception e)
            {
                commProvider = null;
                throw e;
            }
        }
        
        // connect to data stream
        try
        {
            reader = new BufferedReader(new InputStreamReader(commProvider.getInputStream()));
            NMEAGpsSensor.log.info("Connected to NMEA data stream");
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error while initializing communications ", e);
        }
        
        // start main measurement thread
        Thread t = new Thread(new Runnable()
        {
            public void run()
            {
                while (started)
                {
                    pollAndSendMeasurement();
                }
            }
        });
        
        started = true;
        t.start();
    }
    
    
    private synchronized void pollAndSendMeasurement()
    {
        try
        {
            // read next message
            String msg = reader.readLine();
            long msgTime = System.currentTimeMillis();
            NMEAGpsSensor.log.debug("Received message: {}", msg);
            
            // discard messages not starting with $ or with wrong checksum
            if (msg.charAt(0) != '$' || !validateChecksum(msg))
                return;
            
            // extract NMEA message type (remove $TalkerID prefix)
            int firstSep = msg.indexOf(',');
            String msgID = msg.substring(3, firstSep);
            
            // let each registered output handle this message
            for (ISensorDataInterface output: this.getAllOutputs().values())
            {
                NMEAGpsOutput nmeaOut = (NMEAGpsOutput)output;
                nmeaOut.handleMessage(msgTime, msgID, msg);
            }
        }
        catch (EOFException e)
        {
            // do nothing
            // this happens when reader is closed in stop() method
            started = false;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error while parsing NMEA stream", e);
        }
    }
    
    
    /*
     * Check message is error free
     */
    protected boolean validateChecksum(String msg)
    {
        int checkSumIndex = msg.lastIndexOf('*');
        if (checkSumIndex > 0)
        {
            // extract message checksum
            int msgCheckSum = Integer.parseInt(msg.substring(checkSumIndex + 1), 16);
            
            // compute our own checksum
            int checkSum = 0;
            for (int i = 1; i < checkSumIndex; i++)
                checkSum ^= (byte)msg.charAt(i);
                        
            // warn and return false if not equal
            if (checkSum != msgCheckSum)
            {
                log.warn("Wrong message checksum: {}", msg);
                return false;
            }
        }
        
        return true;
    }
    

    @Override
    public void stop() throws SensorHubException
    {
        started = false;
        
        if (reader != null)
        {
            try { reader.close(); }
            catch (IOException e) { }
            reader = null;
        }
        
        if (commProvider != null)
        {
            commProvider.stop();
            commProvider = null;
        }
    }
    

    @Override
    public void cleanup() throws SensorHubException
    {
       
    }
    
    
    @Override
    public boolean isConnected()
    {
        return (commProvider != null);
    }
}

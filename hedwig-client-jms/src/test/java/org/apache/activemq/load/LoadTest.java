/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.load;


import javax.jms.Topic;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;
import junit.framework.TestCase;
import org.apache.hedwig.JmsTestBase;
import org.apache.hedwig.jms.spi.HedwigConnectionFactoryImpl;

import org.junit.Ignore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// For now, ignore it ...
@Ignore
public class LoadTest extends JmsTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(LoadTest.class);

    protected LoadController controller;
    protected LoadClient[] clients;
    protected ConnectionFactory factory;
    protected Destination destination;
    protected int numberOfClients = 50;
    protected int deliveryMode = DeliveryMode.PERSISTENT;
    protected int batchSize = 1000;
    protected int numberOfBatches = 10;
    protected int timeout = Integer.MAX_VALUE;
    protected boolean connectionPerMessage = false;
    protected Connection managementConnection;
    protected Session managementSession;

    /**
     * Sets up a test where the producer and consumer have their own connection.
     *
     * @see junit.framework.TestCase#setUp()
     */
    protected void setUp() throws Exception {
        super.setUp();
        factory = createConnectionFactory();
        managementConnection = factory.createConnection();
        managementSession = managementConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Destination startDestination = createDestination(managementSession, getClass()+".start");
        Destination endDestination = createDestination(managementSession, getClass()+".end");
        LOG.info("Running with " + numberOfClients + " clients - sending "
                + numberOfBatches + " batches of " + batchSize + " messages");
        controller = new LoadController("Controller",factory);
        controller.setBatchSize(batchSize);
        controller.setNumberOfBatches(numberOfBatches);
        controller.setDeliveryMode(deliveryMode);
        controller.setConnectionPerMessage(connectionPerMessage);
        controller.setStartDestination(startDestination);
        controller.setNextDestination(endDestination);
        controller.setTimeout(timeout);
        clients = new LoadClient[numberOfClients];
        for (int i = 0; i < numberOfClients; i++) {
            Destination inDestination = null;
            if (i==0) {
                inDestination = startDestination;
            }else {
                inDestination = createDestination(managementSession, getClass() + ".client."+(i));
            }
            Destination outDestination = null;
            if (i==(numberOfClients-1)) {
                outDestination = endDestination;
            }else {
                outDestination = createDestination(managementSession, getClass() + ".client."+(i+1));
            }
            LoadClient client = new LoadClient("client("+i+")",factory);
            client.setTimeout(timeout);
            client.setDeliveryMode(deliveryMode);
            client.setConnectionPerMessage(connectionPerMessage);
            client.setStartDestination(inDestination);
            client.setNextDestination(outDestination);
            clients[i] = client;
        }
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        managementConnection.close();
        for (int i = 0; i < numberOfClients; i++) {
            clients[i].stop();
        }
        controller.stop();
    }

    protected Destination createDestination(Session s, String destinationName) throws JMSException {
        return s.createTopic(destinationName);
    }

    protected HedwigConnectionFactoryImpl createConnectionFactory() throws Exception {
        return new HedwigConnectionFactoryImpl();
    }

    public void testLoad() throws JMSException, InterruptedException {
        for (int i = 0; i < numberOfClients; i++) {
            clients[i].start();
        }
        controller.start();
        assertEquals((batchSize* numberOfBatches),controller.awaitTestComplete());
    }

}

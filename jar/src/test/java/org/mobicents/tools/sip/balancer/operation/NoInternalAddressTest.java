/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.tools.sip.balancer.operation;

import java.util.Properties;

import javax.sip.address.SipURI;
import javax.sip.header.RecordRouteHeader;

import junit.framework.TestCase;

import org.mobicents.tools.sip.balancer.AppServer;
import org.mobicents.tools.sip.balancer.BalancerRunner;
import org.mobicents.tools.sip.balancer.EventListener;

public class NoInternalAddressTest extends TestCase{
	BalancerRunner balancer;
	int numNodes = 2;
	AppServer[] servers = new AppServer[numNodes];
	Shootist shootist;
	

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		shootist = new Shootist();
		
		balancer = new BalancerRunner();
		Properties properties = new Properties();
		properties.setProperty("javax.sip.STACK_NAME", "SipBalancerForwarder");
		properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
		// You need 16 for logging traces. 32 for debug + traces.
		// Your code will limp at 32 but it is best for debugging.
		properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "LOG4J");
		properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
				"logs/sipbalancerforwarderdebug.txt");
		properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
				"logs/sipbalancerforwarder.xml");
		properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "2");
		properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
		properties.setProperty("gov.nist.javax.sip.CANCEL_CLIENT_TRANSACTION_CHECKED", "false");
		
		properties.setProperty("externalHost", "127.0.0.1");
		properties.setProperty("internalPort", "5065");
		properties.setProperty("externalPort", "5060");
		balancer.start(properties);
		
		
		for(int q=0;q<servers.length;q++) {
			servers[q] = new AppServer("node" + q,4060+q, "127.0.0.1", 2000, 5060, 5060, "0");
			servers[q].start();
		}
		Thread.sleep(5000);
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
		for(int q=0;q<servers.length;q++) {
			servers[q].stop();
		}
		shootist.stop();
		balancer.stop();
	}
	
	public void testFailDetection() throws Exception {
			
			String[] nodes = balancer.getNodeList();
			assertEquals(numNodes, nodes.length);
			servers[0].sendHeartbeat = false;
			Thread.sleep(10500);
			nodes = balancer.getNodeList();
			assertEquals(numNodes-1, nodes.length);
	}

	public void testAllNodesDead() throws Exception {
		for(AppServer as:servers) {
			as.sendCleanShutdownToBalancers();
			as.sendHeartbeat=false;
		}
		Thread.sleep(1000);
		shootist.callerSendsBye = true;
		shootist.sendInitialInvite();

		Thread.sleep(5000);
		assertEquals(500, shootist.responses.get(0).getStatusCode());
	}

//	private void _BAD_testInviteTx() throws Exception {
//		ProtocolObjects senderProtocolObjects = new ProtocolObjects("forward-udp-sender",
//				"gov.nist", "udp", false, null);
//		TestSipListener sender = new TestSipListener(5080, 5060, senderProtocolObjects, true);
//		SipProvider senderProvider = sender.createProvider();
//
//
//		senderProvider.addSipListener(sender);
//
//		senderProtocolObjects.start();
//
//		String fromName = "forward-tcp-sender";
//		String fromSipAddress = "sip-servlets.com";
//		SipURI fromAddress = senderProtocolObjects.addressFactory.createSipURI(
//				fromName, fromSipAddress);
//		
//		String toSipAddress = "sip-servlets.com";
//		String toUser = "forward-receiver";
//		SipURI toAddress = senderProtocolObjects.addressFactory.createSipURI(
//				toUser, toSipAddress);
//		
//		sender.sendSipRequest("INVITE", fromAddress, toAddress, null, null, false);
//		Thread.sleep(20000);
//	}
	
	public void testServerActingAsUASKillOneNodeFailoverResponse() throws Exception {
		EventListener failureEventListener = new EventListener() {
			boolean once = false;
			@Override
			public synchronized void uasAfterResponse(int statusCode, final AppServer source) {
				if(!once) {
					once = true;
					System.out.println("HERE " + once);
					new Thread() {
						public void run() {
							source.sendCleanShutdownToBalancers();
						}
					}.start();
					
					
				}
				
			}
			
			@Override
			public void uasAfterRequestReceived(String method, AppServer source) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void uacAfterRequestSent(String method, AppServer source) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void uacAfterResponse(int statusCode, AppServer source) {
				// TODO Auto-generated method stub
				
			}
		};
		for(AppServer as:servers) as.setEventListener(failureEventListener);
		shootist.callerSendsBye = true;
		shootist.sendInitialInvite();
		Thread.sleep(10000);
		if(balancer.getNodes().size()!=1) fail("Expected one dead node");
	}
	AppServer ringingAppServer;
	AppServer okAppServer;
	public void testASactingAsUAC() throws Exception {
		
		EventListener failureEventListener = new EventListener() {
			
			@Override
			public void uasAfterResponse(int statusCode, AppServer source) {
				
				
			}
			
			@Override
			public void uasAfterRequestReceived(String method, AppServer source) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void uacAfterRequestSent(String method, AppServer source) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void uacAfterResponse(int statusCode, AppServer source) {
				if(statusCode == 180) {
					ringingAppServer = source;
					source.sendCleanShutdownToBalancers();		
				} else {
					okAppServer = source;
					
				}
			}
		};
		for(AppServer as:servers) as.setEventListener(failureEventListener);
		shootist.callerSendsBye = true;
		
		String fromName = "sender";
		String fromHost = "sip-servlets.com";
		SipURI fromAddress = servers[0].protocolObjects.addressFactory.createSipURI(
				fromName, fromHost);
				
		String toUser = "replaces";
		String toHost = "sip-servlets.com";
		SipURI toAddress = servers[0].protocolObjects.addressFactory.createSipURI(
				toUser, toHost);
		
		SipURI ruri = servers[0].protocolObjects.addressFactory.createSipURI(
				"usera", "127.0.0.1:5033");
		ruri.setLrParam();
		SipURI route = servers[0].protocolObjects.addressFactory.createSipURI(
				"lbaddress_noInternalPort", "127.0.0.1:5060");
		route.setParameter("node_host", "127.0.0.1");
		route.setParameter("node_port", "4060");
		route.setLrParam();
		shootist.start();
//		servers[0].sipListener.sendSipRequest("INVITE", fromAddress, toAddress, null, null, false);
		servers[0].sipListener.sendSipRequest("INVITE", fromAddress, toAddress, null, route, false, null, null, ruri);
		Thread.sleep(16000);
		assertTrue(shootist.inviteRequest.getHeader(RecordRouteHeader.NAME).toString().contains("node_host"));
		assertNotSame(ringingAppServer, okAppServer);
		assertNotNull(ringingAppServer);
		assertNotNull(okAppServer);
	}

}

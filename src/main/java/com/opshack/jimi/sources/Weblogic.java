package com.opshack.jimi.sources;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.naming.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Weblogic extends JmxSource {
	
	final private Logger log = LoggerFactory.getLogger(this.getClass());
	
	private JMXConnector jmxConnector;
	private MBeanServerConnection mbeanServerConnection;
	
	public synchronized MBeanServerConnection getMbeanServerConnection() throws InterruptedException {
		
		log.info(this + " connect");
		if (!super.isConnected()) {
			
			String protocol = "t3";
			String mserverURL = "/jndi/weblogic.management.mbeanservers.runtime";

			JMXServiceURL serviceURL = null;
			
			try {
				serviceURL = new JMXServiceURL(protocol, this.getHost(), this.getPort(), mserverURL);
			
			} catch (MalformedURLException e) {
				
				log.error(this + " MalformedURLException : " + protocol + "://" + this.getHost() + ":"  + this.getPort() + mserverURL);
				throw new InterruptedException();		
			}
			
			log.debug(this + " serviceURL " + protocol + "://" + this.getHost() + ":"  + this.getPort() + mserverURL);
			
			Map<String,Object> h = new HashMap<String, Object>();
			
			h.put(Context.SECURITY_PRINCIPAL, this.getUsername());
			h.put(Context.SECURITY_CREDENTIALS, this.getPassword());
			h.put(JMXConnectorFactory.PROTOCOL_PROVIDER_PACKAGES, "weblogic.management.remote");
			h.put("jmx.remote.x.request.waiting.timeout", Long.valueOf(10000));
			
			try {
				this.jmxConnector = JMXConnectorFactory.newJMXConnector(serviceURL, h);
				this.jmxConnector.connect();
				this.mbeanServerConnection = this.jmxConnector.getMBeanServerConnection();

			} catch (IOException e) {
				
				log.error(this + " IO Exception : " + protocol + "://" + this.getHost() + ":"  + this.getPort() + mserverURL);
				
				Thread.sleep(30000); // sleep 30 seconds then mark thread as broken and interrupt it
				
				this.setBroken(true);
				throw new InterruptedException();	
			}
		}
		
		return this.mbeanServerConnection;
		
	}
	
}
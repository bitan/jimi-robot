package com.opshack.jimi;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeDataSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opshack.jimi.sources.Source;
import com.opshack.jimi.sources.SourceState;
import com.opshack.jimi.writers.Writer;

public class Metric implements Runnable {

	final private Logger log = LoggerFactory.getLogger(this.getClass());	
	
	private Source source;
	private HashMap metricDef;
	private ObjectName objectName;
	private HashMap<String, ObjectInstance> beans = new HashMap<String, ObjectInstance>();
	
	
	public Metric(Source source, Map metricDef) throws IOException, MalformedObjectNameException, NullPointerException, InstanceNotFoundException {

		this.source = source;
		
		log.debug(this.source + " " + metricDef + " creat metric");
		this.metricDef = (HashMap) metricDef;
		
		this.objectName = new ObjectName((String) this.metricDef.get("mbean"));
		
		if (this.source.getState().equals(SourceState.CONNECTED)) {
			
			log.debug(this.source + " " + this.metricDef + " getting mbean(s)");
			Set<ObjectInstance> beans = new HashSet<ObjectInstance>();
			
			if (this.objectName.isPattern()) {
				
				beans.addAll(this.source.getMBeanServerConnection().queryMBeans(this.objectName, null));
				
			} else {
				
				beans.add(this.source.getMBeanServerConnection().getObjectInstance(this.objectName));
			}
			
			log.debug(this.source + " " + this.metricDef + " got " + beans.size() + " bean(s)");
			
			String filter = "";
			if (this.metricDef.get("filter") != null) {
				filter = (String) this.metricDef.get("filter");
			}
			
			String attr = (String) this.metricDef.get("attr");
			if (attr != null) {
				for (ObjectInstance bean: beans) {
					
					if (bean.getObjectName().toString().contains(filter)) { 	// filter out beans
						String label = getLabel(bean.getObjectName());
						this.beans.put(label, bean);
					}
				}
			}
		}
	}
	
	
	private String getLabel(ObjectName objectName) {
		
		String label = (String) this.metricDef.get("label");
		
		//TODO find a better way to manage Upper/Lower case in "name"
		String name = objectName.getKeyProperty("Name");
		
		if (name == null) {
			name = objectName.getKeyProperty("name");
		}
		
		if (name == null) {
			return label.replace("$Name", "Undefined");
		}
		
		log.debug("Name : " + name + "-" + objectName);
		
		return label.replace("$Name", name.replaceAll("\\W", ""));
	}
	
	
	public void run() {

		final long ts = System.currentTimeMillis();
		if (this.source.getState().equals(SourceState.CONNECTED) && this.source.getMBeanServerConnection() != null) {

			Set<String> labels = this.beans.keySet();
			for (String label: labels) {

				try {
				
					ObjectInstance bean = this.beans.get(label);
					Object value = null;
					
					synchronized(this.source) {
						value = this.source.getMBeanServerConnection().getAttribute(bean.getObjectName(), (String) this.metricDef.get("attr"));
					}
					
					if (value != null) {

						log.debug(this.source + " " + bean.getObjectName() + 
								" attribute " + this.metricDef.get("attr") +  " value is " + String.valueOf(value));

						if (value instanceof CompositeDataSupport) {

							Object subvalue = null;
							String subattr = (String) this.metricDef.get("subattr");

							if (subattr != null) {
								subvalue = ((CompositeDataSupport) value).get(subattr);

								if (subvalue != null && (subvalue instanceof Long || subvalue instanceof Integer)) {

									this.write(new Event(this.source.getProps(), this.metricDef, label, String.valueOf(subvalue), ts));

								} else {
									log.error(this.source + " " + bean.getObjectName() + 
											" got UNSUPPORTED value " + String.valueOf(subvalue) );
								}

							} else {
								log.error(this.source + " " + bean.getObjectName() + 
										" attr is of CompositeData type, you have to provide subattr parameter.");
							}
				
						} else if (value instanceof Long || value instanceof Integer) {

							this.write(new Event(this.source.getProps(), this.metricDef, label, String.valueOf(value), ts));

						} else  {
							log.error(this.source + " " + bean.getObjectName() + 
									" got UNSUPPORTED value " + String.valueOf(value) );
						}
					} 
					
				} catch (IOException e) {
					
					this.source.setState(SourceState.BROKEN);
					
					log.warn(this.source + " IOException: " + e.getMessage());

					if (log.isDebugEnabled()) {
						e.printStackTrace();
					}

				} catch (Exception e) {      
					
					log.warn(this.source + " non-IOException: " + e.getMessage());

					if (log.isDebugEnabled()) {
						e.printStackTrace();
					}
				}
			}
		}
	} 
	
	private void write(Event event) throws InterruptedException {
		
		for (Writer writer: this.source.getWriters()) {
			writer.put(event);
		}
	}
}
	

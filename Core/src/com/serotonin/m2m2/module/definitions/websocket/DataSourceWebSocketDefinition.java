/**
 * Copyright (C) 2016 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.module.definitions.websocket;

import com.serotonin.m2m2.module.WebSocketDefinition;
import com.serotonin.m2m2.web.mvc.websocket.MangoWebSocketHandler;
import com.serotonin.m2m2.web.mvc.websocket.publisher.DataSourceWebSocketHandler;

/**
 * @author Terry Packer
 *
 */
public class DataSourceWebSocketDefinition extends WebSocketDefinition{

	public static final DataSourceWebSocketHandler handler = new DataSourceWebSocketHandler();
	
	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.module.WebSocketDefinition#getHandlerSingleton()
	 */
	@Override
	public MangoWebSocketHandler getHandler() {
		return handler;
	}

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.module.WebSocketDefinition#getUrl()
	 */
	@Override
	public String getUrl() {
		return "/v1/websocket/data-sources";
	}
	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.module.WebSocketDefinition#perConnection()
	 */
	@Override
	public boolean perConnection() {
		return false;
	}
}
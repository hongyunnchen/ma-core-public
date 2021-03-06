/**
 * Copyright (C) 2016 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.module.definitions.event.handlers;

import com.serotonin.m2m2.module.EventHandlerDefinition;
import com.serotonin.m2m2.vo.event.ProcessEventHandlerVO;
import com.serotonin.m2m2.web.mvc.rest.v1.model.events.handlers.AbstractEventHandlerModel;
import com.serotonin.m2m2.web.mvc.rest.v1.model.events.handlers.ProcessEventHandlerModel;

/**
 * @author Terry Packer
 *
 */
public class ProcessEventHandlerDefinition extends EventHandlerDefinition<ProcessEventHandlerVO>{
	
	public static final String TYPE_NAME = "PROCESS";
	public static final String DESC_KEY = "eventHandlers.type.process";
	
	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.module.EventHandlerDefinition#getEventHandlerTypeName()
	 */
	@Override
	public String getEventHandlerTypeName() {
		return TYPE_NAME;
	}

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.module.EventHandlerDefinition#getDescriptionKey()
	 */
	@Override
	public String getDescriptionKey() {
		return DESC_KEY;
	}

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.module.EventHandlerDefinition#createEventHandlerVO()
	 */
	@Override
	protected ProcessEventHandlerVO createEventHandlerVO() {
		return new ProcessEventHandlerVO();
	}

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.module.EventHandlerDefinition#getModelClass()
	 */
	@Override
	public Class<? extends AbstractEventHandlerModel<?>> getModelClass() {
		return ProcessEventHandlerModel.class;
	}
}

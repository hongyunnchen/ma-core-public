/**
 * Copyright (C) 2015 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.infiniteautomation.mango.io.serial.virtual;

import java.lang.reflect.Type;

import com.serotonin.ShouldNeverHappenException;
import com.serotonin.json.JsonException;
import com.serotonin.json.spi.TypeResolver;
import com.serotonin.json.type.JsonObject;
import com.serotonin.json.type.JsonValue;
import com.serotonin.m2m2.i18n.TranslatableJsonException;

/**
 * @author Terry Packer
 *
 */
public class VirtualSerialPortConfigResolver implements TypeResolver {
	
    @Override
    public Type resolve(JsonValue jsonValue) throws JsonException {
        if (jsonValue == null)
            return null;

        JsonObject json = jsonValue.toJsonObject();

        String text = json.getString("type");
        if (text == null)
            throw new TranslatableJsonException("emport.error.virtual.comm.missing", "type",
            		VirtualSerialPortConfig.PORT_TYPE_CODES);

        int type = VirtualSerialPortConfig.PORT_TYPE_CODES.getId(text);
        if (!VirtualSerialPortConfig.PORT_TYPE_CODES.isValidId(type))
            throw new TranslatableJsonException("emport.error.virtual.comm.invalid", "type", text,
            		VirtualSerialPortConfig.PORT_TYPE_CODES.getCodeList());

        if (type == VirtualSerialPortConfig.SerialPortTypes.JSSC)
            throw new ShouldNeverHappenException("JSSC Ports are not virtual");
        if (type == VirtualSerialPortConfig.SerialPortTypes.SERIAL_SOCKET_BRIDGE)
            return SerialSocketBridgeConfig.class;
        return SerialSocketBridgeConfig.class;
    }
}
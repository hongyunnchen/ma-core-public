/*
 * Copyright (C) 2017 Infinite Automation Software. All rights reserved.
 */
package com.serotonin.m2m2.web.mvc.rest.v1.converters;

import java.io.IOException;
import java.lang.reflect.Type;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.converter.json.MappingJacksonInputMessage;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.util.TypeUtils;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

/**
 * @author Jared Wiltshire
 */
public class JacksonCsvConverter extends AbstractJackson2HttpMessageConverter {

    CsvMapper csvMapper;
    
    public JacksonCsvConverter() {
        this(new CsvMapper());
    }
    
    public JacksonCsvConverter(CsvMapper csvMapper) {
        super(csvMapper, new MediaType("text", "csv"));
        this.csvMapper = csvMapper;
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {

        JavaType javaType = getJavaType(clazz, null);
        return readJavaType(javaType, inputMessage);
    }

    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {

        JavaType javaType = getJavaType(type, contextClass);
        return readJavaType(javaType, inputMessage);
    }
    
    private Object readJavaType(JavaType javaType, HttpInputMessage inputMessage) {
        try {
            if (inputMessage instanceof MappingJacksonInputMessage) {
                Class<?> deserializationView = ((MappingJacksonInputMessage) inputMessage).getDeserializationView();
                if (deserializationView != null) {
                    return this.objectMapper.readerWithView(deserializationView)
                            .forType(javaType)
                            .with(CsvSchema.emptySchema().withHeader())
                            .readValue(inputMessage.getBody());
                }
            }
            return this.objectMapper.reader()
                    .forType(javaType)
                    .with(CsvSchema.emptySchema().withHeader())
                    .readValue(inputMessage.getBody());
        }
        catch (IOException ex) {
            throw new HttpMessageNotReadableException("Could not read document: " + ex.getMessage(), ex);
        }
    }
    
    @Override
    protected void writeInternal(Object object, Type type, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {

        MediaType contentType = outputMessage.getHeaders().getContentType();
        JsonEncoding encoding = getJsonEncoding(contentType);
        JsonGenerator generator = this.objectMapper.getFactory().createGenerator(outputMessage.getBody(), encoding);
        try {
            writePrefix(generator, object);

            Class<?> serializationView = null;
            FilterProvider filters = null;
            Object value = object;
            JavaType javaType = null;
            if (object instanceof MappingJacksonValue) {
                MappingJacksonValue container = (MappingJacksonValue) object;
                value = container.getValue();
                serializationView = container.getSerializationView();
                filters = container.getFilters();
            }
            if (type != null && value != null && TypeUtils.isAssignable(type, value.getClass())) {
                javaType = getJavaType(type, null);
            }
            ObjectWriter objectWriter;
            if (serializationView != null) {
                objectWriter = this.objectMapper.writerWithView(serializationView);
            }
            else if (filters != null) {
                objectWriter = this.objectMapper.writer(filters);
            }
            else {
                objectWriter = this.objectMapper.writer();
            }

            if (javaType != null && javaType.isContainerType()) {
                objectWriter = objectWriter.forType(javaType);
            }

            if (javaType != null) {
                CsvSchema schema = csvMapper.schemaFor(javaType).withHeader();
                objectWriter = objectWriter.with(schema);
            }
            
//            SerializationConfig config = objectWriter.getConfig();
//            if (contentType != null && contentType.isCompatibleWith(TEXT_EVENT_STREAM) &&
//                    config.isEnabled(SerializationFeature.INDENT_OUTPUT)) {
//                objectWriter = objectWriter.with(this.ssePrettyPrinter);
//            }
            objectWriter.writeValue(generator, value);

            writeSuffix(generator, object);
            generator.flush();

        }
        catch (JsonProcessingException ex) {
            throw new HttpMessageNotWritableException("Could not write content: " + ex.getMessage(), ex);
        }
    }
}

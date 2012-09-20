/*
 * Copyright 2012 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.marklogic.client.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.marklogic.client.MarkLogicIOException;
import com.marklogic.client.io.marker.BufferableHandle;
import com.marklogic.client.io.marker.XMLReadHandle;
import com.marklogic.client.io.marker.XMLWriteHandle;

/**
 * A JAXB Handle roundtrips a POJO (a Java data structure) to and from a database document.
 * 
 * The POJO class must have JAXB annotations or must be generated by xjc from an XML Schema.
 * 
 * The JAXB Handle must be initialized with a JAXB Context with which the root POJO classes
 * have been registered.
 */
public class JAXBHandle
	extends BaseHandle<InputStream, OutputStreamSender>
    implements OutputStreamSender, BufferableHandle,
        XMLReadHandle, XMLWriteHandle
{
	static final private Logger logger = LoggerFactory.getLogger(JAXBHandle.class);

	private JAXBContext context;
	private Object      content;

	/**
	 * Initializes the JAXB handle with the JAXB context for the classes
	 * of the marshalled or unmarshalled structure.
	 * @param context	the JAXB context
	 */
	public JAXBHandle(JAXBContext context) {
		super();
		super.setFormat(Format.XML);
   		setResendable(true);
		this.context = context;
	}

	/**
	 * Returns the root object of the JAXB structure for the content.
	 * @return	the root JAXB object
	 */
	public Object get() {
		return content;
	}
	/**
	 * Assigns the root object of the JAXB structure for the content.
	 * @param content	the root JAXB object
	 */
    public void set(Object content) {
    	this.content = content;
    }
    /**
	 * Assigns the root object of the JAXB structure for the content
	 * and returns the handle as a fluent convenience.
	 * @param content	the root JAXB object
	 * @return	this handle
     */
    public JAXBHandle with(Object content) {
    	set(content);
    	return this;
    }

	/**
	 * Restricts the format to XML.
	 */
	@Override
    public void setFormat(Format format) {
		if (format != Format.XML)
			throw new IllegalArgumentException("JAXBHandle supports the XML format only");
	}
	/**
	 * Specifies the mime type of the content and returns the handle
	 * as a fluent convenience.
	 * @param mimetype	the mime type of the content
	 * @return	this handle
	 */
	public JAXBHandle withMimetype(String mimetype) {
		setMimetype(mimetype);
		return this;
	}

	/**
     * fromBuffer() unmarshals a JAXB POJO from a byte array
     * buffer.  The buffer must store the marshaled XML for the 
     * JAXB POJO in UTF-8 encoding. JAXB cannot unmarshal arbitrary XML.
	 */
	@Override
	public void fromBuffer(byte[] buffer) {
		if (buffer == null || buffer.length == 0)
			content = null;
		else
			receiveContent(new ByteArrayInputStream(buffer));
	}
	@Override
	public byte[] toBuffer() {
		try {
			if (content == null)
				return null;

			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			write(buffer);

			return buffer.toByteArray();
		} catch (IOException e) {
			throw new MarkLogicIOException(e);
		}
	}
	/**
	 * Returns the JAXB structure as an XML string.
	 */
	@Override
	public String toString() {
		try {
			return new String(toBuffer(),"UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new MarkLogicIOException(e);
		}
	}

	protected Marshaller makeMarshaller(JAXBContext context) throws JAXBException {
		Marshaller marshaller = context.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_ENCODING,         "UTF-8");
		return marshaller;
	}

	@Override
	protected Class<InputStream> receiveAs() {
    	return InputStream.class;
    }
    @Override
	protected void receiveContent(InputStream content) {
		try {
			Unmarshaller unmarshaller = context.createUnmarshaller();
			this.content = unmarshaller.unmarshal(new InputStreamReader(content, "UTF-8"));
		} catch (JAXBException e) {
			logger.error("Failed to unmarshall object read from database document",e);
			throw new MarkLogicIOException(e);
		} catch (UnsupportedEncodingException e) {
			logger.error("Failed to unmarshall object read from database document",e);
			throw new MarkLogicIOException(e);
		}
	}
    @Override
	protected OutputStreamSender sendContent() {
		if (content == null) {
			throw new IllegalStateException("No object to write");
		}

		return this;
	}

	@Override
	public void write(OutputStream out) throws IOException {
		try {
			Marshaller marshaller = makeMarshaller(context);
			marshaller.marshal(content, out);
		} catch (JAXBException e) {
			logger.error("Failed to marshall object for writing to database document",e);
			throw new MarkLogicIOException(e);
		}
	}
}

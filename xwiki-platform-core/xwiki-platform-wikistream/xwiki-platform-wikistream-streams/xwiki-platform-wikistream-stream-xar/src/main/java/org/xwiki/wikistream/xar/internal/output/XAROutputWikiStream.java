/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
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
package org.xwiki.wikistream.xar.internal.output;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.filter.FilterEventParameters;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.wikistream.WikiStreamException;
import org.xwiki.wikistream.filter.WikiClassFilter;
import org.xwiki.wikistream.filter.WikiObjectFilter;
import org.xwiki.wikistream.internal.output.AbstractBeanOutputWikiStream;
import org.xwiki.wikistream.xar.internal.XARAttachmentModel;
import org.xwiki.wikistream.xar.internal.XARClassModel;
import org.xwiki.wikistream.xar.internal.XARClassPropertyModel;
import org.xwiki.wikistream.xar.internal.XARDocumentModel;
import org.xwiki.wikistream.xar.internal.XARFilter;
import org.xwiki.wikistream.xar.internal.XARObjectModel;
import org.xwiki.wikistream.xar.internal.XARObjectPropertyModel;
import org.xwiki.wikistream.xml.internal.output.WikiStreamXMLStreamWriter;
import org.xwiki.wikistream.xwiki.filter.XWikiWikiAttachmentFilter;
import org.xwiki.wikistream.xwiki.filter.XWikiWikiDocumentFilter;

/**
 * @version $Id$
 * @since 5.2M2
 */
@Component
@Named("xwiki+xar/1.0")
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class XAROutputWikiStream extends AbstractBeanOutputWikiStream<XAROutputProperties> implements XARFilter
{
    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    private XARWikiWriter wikiWriter;

    private String currentSpace;

    private String currentDocument;

    private FilterEventParameters currentDocumentParameters;

    private FilterEventParameters currentDocumentLocaleParameters;

    private FilterEventParameters currentDocumentRevisionParameters;

    private LocalDocumentReference currentDocumentReference;

    private Locale currentDocumentLocale = Locale.ROOT;

    private String currentDocumentVersion;

    private String currentAttachment;

    private String currentObjectClass;

    private WikiStreamXMLStreamWriter writer;

    public String toString(Object obj)
    {
        return ObjectUtils.toString(obj, null);
    }

    public String toString(Date date)
    {
        return date != null ? String.valueOf(date.getTime()) : null;
    }

    public String toString(Syntax syntax)
    {
        return syntax != null ? syntax.toIdString() : null;
    }

    public String toString(byte[] bytes)
    {
        return Base64.encodeBase64String(bytes);
    }

    public WikiStreamXMLStreamWriter getWriter() throws WikiStreamException
    {
        if (this.writer == null) {
            if (this.wikiWriter != null) {
                this.writer =
                    new WikiStreamXMLStreamWriter(this.wikiWriter.newEntry(this.currentDocumentReference,
                        this.currentDocumentLocale), this.properties);
            } else {
                this.writer = new WikiStreamXMLStreamWriter(this.properties);
            }

            this.writer.writeStartDocument();
            this.writer.writeStartElement(XARDocumentModel.ELEMENT_DOCUMENT);
            this.writer.writeElement(XARDocumentModel.ELEMENT_SPACE, this.currentSpace);
            this.writer.writeElement(XARDocumentModel.ELEMENT_NAME, this.currentDocument);

            this.writer.writeElement(XARDocumentModel.ELEMENT_LOCALE, toString(this.currentDocumentLocale));
            this.writer.writeElement(XARDocumentModel.ELEMENT_DEFAULTLOCALE,
                toString(this.currentDocumentParameters.get(XWikiWikiDocumentFilter.PARAMETER_LOCALE)));
            this.writer.writeElement(XARDocumentModel.ELEMENT_ISTRANSLATION, this.currentDocumentLocale != null
                && !Locale.ROOT.equals(this.currentDocumentLocale) ? "1" : "0");

            this.writer.writeElement(XARDocumentModel.ELEMENT_CREATION_AUTHOR,
                (String) this.currentDocumentLocaleParameters.get(XWikiWikiDocumentFilter.PARAMETER_CREATION_AUTHOR));
            this.writer.writeElement(XARDocumentModel.ELEMENT_CREATION_DATE,
                toString((Date) this.currentDocumentLocaleParameters
                    .get(XWikiWikiDocumentFilter.PARAMETER_CREATION_DATE)));

            this.writer.writeElement(XARDocumentModel.ELEMENT_PARENT,
                (String) this.currentDocumentRevisionParameters.get(XWikiWikiDocumentFilter.PARAMETER_PARENT));
            this.writer.writeElement(XARDocumentModel.ELEMENT_REVISION_AUTHOR,
                (String) this.currentDocumentRevisionParameters.get(XWikiWikiDocumentFilter.PARAMETER_REVISION_AUTHOR));
            this.writer.writeElement(XARDocumentModel.ELEMENT_CUSTOMCLASS,
                (String) this.currentDocumentRevisionParameters.get(XWikiWikiDocumentFilter.PARAMETER_CUSTOMCLASS));
            this.writer.writeElement(XARDocumentModel.ELEMENT_CONTENT_AUTHOR,
                (String) this.currentDocumentRevisionParameters.get(XWikiWikiDocumentFilter.PARAMETER_CONTENT_AUTHOR));
            this.writer.writeElement(XARDocumentModel.ELEMENT_REVISION_DATE,
                toString((Date) this.currentDocumentRevisionParameters
                    .get(XWikiWikiDocumentFilter.PARAMETER_REVISION_DATE)));
            this.writer.writeElement(XARDocumentModel.ELEMENT_CONTENT_DATE,
                toString((Date) this.currentDocumentRevisionParameters
                    .get(XWikiWikiDocumentFilter.PARAMETER_CONTENT_DATE)));
            this.writer.writeElement(XARDocumentModel.ELEMENT_REVISION_VERSION, this.currentDocumentVersion);
            this.writer.writeElement(XARDocumentModel.ELEMENT_TITLE,
                (String) this.currentDocumentRevisionParameters.get(XWikiWikiDocumentFilter.PARAMETER_TITLE));
            this.writer.writeElement(XARDocumentModel.ELEMENT_DEFAULTTEMPLATE,
                (String) this.currentDocumentRevisionParameters.get(XWikiWikiDocumentFilter.PARAMETER_DEFAULTTEMPLATE));
            this.writer
                .writeElement(XARDocumentModel.ELEMENT_VALIDATIONSCRIPT,
                    (String) this.currentDocumentRevisionParameters
                        .get(XWikiWikiDocumentFilter.PARAMETER_VALIDATIONSCRIPT));
            this.writer
                .writeElement(XARDocumentModel.ELEMENT_REVISION_COMMENT,
                    (String) this.currentDocumentRevisionParameters
                        .get(XWikiWikiDocumentFilter.PARAMETER_REVISION_COMMENT));
            this.writer.writeElement(XARDocumentModel.ELEMENT_REVISION_MINOR,
                toString(this.currentDocumentRevisionParameters.get(XWikiWikiDocumentFilter.PARAMETER_REVISION_MINOR)));
            this.writer
                .writeElement(XARDocumentModel.ELEMENT_SYNTAX, toString((Syntax) this.currentDocumentRevisionParameters
                    .get(XWikiWikiDocumentFilter.PARAMETER_SYNTAX)));
            this.writer.writeElement(XARDocumentModel.ELEMENT_ISHIDDEN,
                toString(this.currentDocumentRevisionParameters.get(XWikiWikiDocumentFilter.PARAMETER_HIDDEN)));
        }

        return this.writer;
    }

    // events

    @Override
    public void beginFarm(FilterEventParameters parameters) throws WikiStreamException
    {
    }

    @Override
    public void endFarm(FilterEventParameters parameters) throws WikiStreamException
    {
    }

    @Override
    public void beginWiki(String name, FilterEventParameters parameters) throws WikiStreamException
    {
        this.wikiWriter = new XARWikiWriter(name, parameters, this.properties);
    }

    @Override
    public void endWiki(String name, FilterEventParameters parameters) throws WikiStreamException
    {
        this.wikiWriter.close();

        this.wikiWriter = null;
    }

    @Override
    public void beginWikiSpace(String name, FilterEventParameters parameters) throws WikiStreamException
    {
        this.currentSpace = name;
    }

    @Override
    public void endWikiSpace(String name, FilterEventParameters parameters) throws WikiStreamException
    {
        this.currentSpace = null;
    }

    @Override
    public void beginWikiDocument(String name, FilterEventParameters parameters) throws WikiStreamException
    {
        this.currentDocument = name;
        this.currentDocumentParameters = parameters;

        this.currentDocumentReference = new LocalDocumentReference(this.currentSpace, this.currentDocument);
    }

    @Override
    public void endWikiDocument(String name, FilterEventParameters parameters) throws WikiStreamException
    {
        this.currentDocument = null;
        this.currentDocumentReference = null;
        this.currentDocumentParameters = null;
    }

    @Override
    public void beginWikiDocumentLocale(Locale locale, FilterEventParameters parameters) throws WikiStreamException
    {
        this.currentDocumentLocale = locale;
        this.currentDocumentLocaleParameters = parameters;
    }

    @Override
    public void endWikiDocumentLocale(Locale locale, FilterEventParameters parameters) throws WikiStreamException
    {
        getWriter().writeElement(XARDocumentModel.ELEMENT_REVISIONS,
            (String) parameters.get(XWikiWikiDocumentFilter.PARAMETER_JRCSREVISIONS));

        getWriter().writeEndElement();
        getWriter().writeEndDocument();

        getWriter().close();

        if (this.wikiWriter != null) {
            this.wikiWriter.closeEntry();
        }

        this.writer = null;
        this.currentDocumentLocale = null;
    }

    @Override
    public void beginWikiDocumentRevision(String version, FilterEventParameters parameters) throws WikiStreamException
    {
        this.currentDocumentVersion = version;
        this.currentDocumentRevisionParameters = parameters;
    }

    @Override
    public void endWikiDocumentRevision(String version, FilterEventParameters parameters) throws WikiStreamException
    {
        getWriter().writeElement(XARDocumentModel.ELEMENT_CONTENT,
            (String) parameters.get(XWikiWikiDocumentFilter.PARAMETER_CONTENT));

        this.currentDocumentVersion = null;
    }

    @Override
    public void beginWikiAttachment(String name, FilterEventParameters parameters) throws WikiStreamException
    {
        getWriter().writeStartElement(XARAttachmentModel.ELEMENT_ATTACHMENT);

        this.currentAttachment = name;

        this.writer.writeElement(XARAttachmentModel.ELEMENT_NAME, this.currentAttachment);
    }

    @Override
    public void endWikiAttachment(String attachmentName, FilterEventParameters parameters) throws WikiStreamException
    {
        this.writer.writeElement(XARAttachmentModel.ELEMENT_REVISIONS,
            (String) parameters.get(XWikiWikiAttachmentFilter.PARAMETER_JRCSREVISIONS));

        if (this.writer != null) {
            getWriter().writeEndElement();
        }

        this.currentAttachment = null;
    }

    @Override
    public void beginWikiAttachmentRevision(String version, FilterEventParameters parameters)
        throws WikiStreamException
    {
        this.writer.writeElement(XARAttachmentModel.ELEMENT_REVISION_AUTHOR,
            (String) parameters.get(XWikiWikiAttachmentFilter.PARAMETER_REVISION_AUTHOR));
        this.writer.writeElement(XARAttachmentModel.ELEMENT_REVISION_DATE,
            toString((Date) parameters.get(XWikiWikiAttachmentFilter.PARAMETER_REVISION_DATE)));
        this.writer.writeElement(XARAttachmentModel.ELEMENT_REVISION_VERSION, version);
        this.writer.writeElement(XARAttachmentModel.ELEMENT_REVISION_COMMENT,
            (String) parameters.get(XWikiWikiAttachmentFilter.PARAMETER_REVISION_COMMENT));
    }

    @Override
    public void endWikiAttachmentRevision(String version, FilterEventParameters parameters) throws WikiStreamException
    {
    }

    @Override
    public void onWikiAttachmentContent(InputStream content, Long size, FilterEventParameters parameters)
        throws WikiStreamException
    {
        long contentSize = 0;

        this.writer.writeStartElement(XARAttachmentModel.ELEMENT_CONTENT);
        byte[] buffer = new byte[4096];
        int readSize;
        do {
            try {
                readSize = content.read(buffer, 0, 4096);
            } catch (IOException e) {
                throw new WikiStreamException("Failed to read content stream", e);
            }

            if (readSize > 0) {
                String chunk;
                if (readSize == 4096) {
                    chunk = Base64.encodeBase64String(buffer);
                } else {
                    chunk = Base64.encodeBase64String(ArrayUtils.subarray(buffer, 0, readSize));
                }
                this.writer.writeCharacters(chunk);
                contentSize += readSize;
            }
        } while (readSize == 4096);
        this.writer.writeEndElement();

        this.writer.writeElement(XARAttachmentModel.ELEMENT_CONTENT_SIZE, toString(contentSize));
    }

    @Override
    public void beginWikiClass(FilterEventParameters parameters) throws WikiStreamException
    {
        getWriter().writeStartElement(XARClassModel.ELEMENT_CLASS);

        this.writer.writeElement(XARClassModel.ELEMENT_NAME, this.currentObjectClass != null ? this.currentObjectClass
            : this.localSerializer.serialize(this.currentDocumentReference));

        this.writer.writeElement(XARClassModel.ELEMENT_CUSTOMCLASS,
            (String) parameters.get(WikiClassFilter.PARAMETER_CUSTOMCLASS));
        this.writer.writeElement(XARClassModel.ELEMENT_CUSTOMMAPPING,
            (String) parameters.get(WikiClassFilter.PARAMETER_CUSTOMMAPPING));
        this.writer.writeElement(XARClassModel.ELEMENT_DEFAULTVIEWSHEET,
            (String) parameters.get(WikiClassFilter.PARAMETER_SHEET_DEFAULTVIEW));
        this.writer.writeElement(XARClassModel.ELEMENT_DEFAULTEDITSHEET,
            (String) parameters.get(WikiClassFilter.PARAMETER_SHEET_DEFAULTEDIT));
        this.writer.writeElement(XARClassModel.ELEMENT_DEFAULTWEB,
            (String) parameters.get(WikiClassFilter.PARAMETER_DEFAULTSPACE));
        this.writer.writeElement(XARClassModel.ELEMENT_NAMEFIELD,
            (String) parameters.get(WikiClassFilter.PARAMETER_NAMEFIELD));
        this.writer.writeElement(XARClassModel.ELEMENT_VALIDATIONSCRIPT,
            (String) parameters.get(WikiClassFilter.PARAMETER_VALIDATIONSCRIPT));
    }

    @Override
    public void endWikiClass(FilterEventParameters parameters) throws WikiStreamException
    {
        getWriter().writeEndElement();
    }

    @Override
    public void beginWikiClassProperty(String name, String type, FilterEventParameters parameters)
        throws WikiStreamException
    {
        getWriter().writeStartElement(name);
    }

    @Override
    public void endWikiClassProperty(String name, String type, FilterEventParameters parameters)
        throws WikiStreamException
    {
        this.writer.writeElement(XARClassPropertyModel.ELEMENT_CLASSTYPE, type);

        getWriter().writeEndElement();
    }

    @Override
    public void onWikiClassPropertyField(String name, String value, FilterEventParameters parameters)
        throws WikiStreamException
    {
        this.writer.writeElement(name, value);
    }

    @Override
    public void beginWikiObject(String name, FilterEventParameters parameters) throws WikiStreamException
    {
        getWriter().writeStartElement(XARObjectModel.ELEMENT_OBJECT);

        this.currentObjectClass = (String) parameters.get(WikiObjectFilter.PARAMETER_CLASS_REFERENCE);

        this.writer.writeElement(XARObjectModel.ELEMENT_NAME,
            this.localSerializer.serialize(this.currentDocumentReference));
        this.writer.writeElement(XARObjectModel.ELEMENT_NUMBER,
            toString((Integer) parameters.get(WikiObjectFilter.PARAMETER_NUMBER)));
        this.writer.writeElement(XARObjectModel.ELEMENT_CLASSNAME, this.currentObjectClass);
        this.writer.writeElement(XARObjectModel.ELEMENT_GUID, (String) parameters.get(WikiObjectFilter.PARAMETER_GUID));
    }

    @Override
    public void endWikiObject(String name, FilterEventParameters parameters) throws WikiStreamException
    {
        getWriter().writeEndElement();

        this.currentObjectClass = null;
    }

    @Override
    public void beginWikiObjectProperty(String name, String value, FilterEventParameters parameters)
        throws WikiStreamException
    {
        getWriter().writeStartElement(XARObjectPropertyModel.ELEMENT_PROPERTY);

        this.writer.writeElement(name, value);
    }

    @Override
    public void endWikiObjectProperty(String name, String value, FilterEventParameters parameters)
        throws WikiStreamException
    {
        getWriter().writeEndElement();
    }
}

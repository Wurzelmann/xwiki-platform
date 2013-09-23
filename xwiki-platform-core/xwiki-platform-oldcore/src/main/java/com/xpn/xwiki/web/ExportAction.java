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
package com.xpn.xwiki.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSet;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.wikistream.WikiStreamException;
import org.xwiki.wikistream.input.InputWikiStream;
import org.xwiki.wikistream.input.InputWikiStreamFactory;
import org.xwiki.wikistream.instance.internal.input.InstanceInputProperties;
import org.xwiki.wikistream.internal.output.DefaultOutputStreamOutputTarget;
import org.xwiki.wikistream.output.OutputWikiStream;
import org.xwiki.wikistream.output.OutputWikiStreamFactory;
import org.xwiki.wikistream.type.WikiStreamType;
import org.xwiki.wikistream.utils.WikiStreamConstants;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.export.html.HtmlPackager;
import com.xpn.xwiki.internal.export.OfficeExporter;
import com.xpn.xwiki.internal.export.OfficeExporterURLFactory;
import com.xpn.xwiki.pdf.api.PdfExport;
import com.xpn.xwiki.pdf.api.PdfExport.ExportType;
import com.xpn.xwiki.pdf.impl.PdfExportImpl;
import com.xpn.xwiki.pdf.impl.PdfURLFactory;
import com.xpn.xwiki.plugin.packaging.PackageAPI;
import com.xpn.xwiki.util.Util;

/**
 * Exports in XAR, PDF, RTF or HTML formats.
 * 
 * @version $Id$
 */
public class ExportAction extends XWikiAction
{
    @Override
    public String render(XWikiContext context) throws XWikiException
    {
        String defaultPage;

        try {
            XWikiRequest request = context.getRequest();
            String format = request.get("format");

            if ((format == null) || (format.equals("xar"))) {
                defaultPage = exportXAR(context);
            } else if (format.equals("html")) {
                defaultPage = exportHTML(context);
            } else {
                defaultPage = export(format, context);
            }
        } catch (Exception e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_APP, XWikiException.ERROR_XWIKI_APP_EXPORT,
                "Exception while exporting", e);
        }

        return defaultPage;
    }

    /**
     * Create ZIP archive containing wiki pages rendered in HTML, attached files and used skins.
     * 
     * @param context the XWiki context.
     * @return always return null.
     * @throws XWikiException error when exporting HTML ZIP package.
     * @throws IOException error when exporting HTML ZIP package.
     * @since XWiki Platform 1.3M1
     */
    private String exportHTML(XWikiContext context) throws XWikiException, IOException
    {
        XWikiRequest request = context.getRequest();

        String description = request.get("description");
        String name = request.get("name");
        String[] pages = request.getParameterValues("pages");

        List<String> pageList = new ArrayList<String>();
        if (pages == null || pages.length == 0) {
            pageList.add(context.getDoc().getFullName());

            if (StringUtils.isBlank(name)) {
                name = context.getDoc().getFullName();
            }
        } else {
            Map<String, Object[]> wikiQueries = new HashMap<String, Object[]>();
            for (int i = 0; i < pages.length; ++i) {
                String pattern = pages[i];

                String wikiName;
                if (pattern.contains(":")) {
                    int index = pattern.indexOf(':');
                    wikiName = pattern.substring(0, index);
                    pattern = pattern.substring(index + 1);
                } else {
                    wikiName = context.getDatabase();
                }

                StringBuffer where;
                List<String> params;

                if (!wikiQueries.containsKey(wikiName)) {
                    Object[] query = new Object[2];
                    query[0] = where = new StringBuffer("where ");
                    query[1] = params = new ArrayList<String>();
                    wikiQueries.put(wikiName, query);
                } else {
                    Object[] query = wikiQueries.get(wikiName);
                    where = (StringBuffer) query[0];
                    params = (List<String>) query[1];
                }

                if (i > 0) {
                    where.append(" or ");
                }

                where.append("doc.fullName like ?");
                params.add(pattern);
            }

            String database = context.getDatabase();
            try {
                for (Map.Entry<String, Object[]> entry : wikiQueries.entrySet()) {
                    String wikiName = entry.getKey();
                    Object[] query = entry.getValue();
                    String where = ((StringBuffer) query[0]).toString();
                    @SuppressWarnings("unchecked")
                    List<String> params = (List<String>) query[1];

                    context.setDatabase(wikiName);
                    List<String> docsNames = context.getWiki().getStore().searchDocumentsNames(where, params, context);
                    for (String docName : docsNames) {
                        String pageReference = wikiName + XWikiDocument.DB_SPACE_SEP + docName;
                        if (context.getWiki().getRightService()
                            .hasAccessLevel("view", context.getUser(), pageReference, context)) {
                            pageList.add(pageReference);
                        }
                    }
                }
            } finally {
                context.setDatabase(database);
            }
        }

        if (pageList.size() == 0) {
            return null;
        }

        HtmlPackager packager = new HtmlPackager();

        if (name != null && name.trim().length() > 0) {
            packager.setName(name);
        }

        if (description != null) {
            packager.setDescription(description);
        }

        packager.addPages(pageList);

        packager.export(context);

        return null;
    }

    private String export(String format, XWikiContext context) throws XWikiException, IOException
    {
        // We currently use the PDF export infrastructure but we have to redesign the export code.
        XWikiURLFactory urlFactory = new OfficeExporterURLFactory();
        PdfExport exporter = new OfficeExporter();
        // Check if the office exporter supports the specified format.
        ExportType exportType = ((OfficeExporter) exporter).getExportType(format);
        if ("pdf".equalsIgnoreCase(format) || exportType == null) {
            // The export format is PDF or the office converter can't be used (either it doesn't support the specified
            // format or the office server is not started).
            urlFactory = new PdfURLFactory();
            exporter = new PdfExportImpl();
            exportType = ExportType.PDF;
            if ("rtf".equalsIgnoreCase(format)) {
                exportType = ExportType.RTF;
            }
        }

        urlFactory.init(context);
        context.setURLFactory(urlFactory);
        handleRevision(context);

        XWikiDocument doc = context.getDoc();
        context.getResponse().setContentType(exportType.getMimeType());
        context.getResponse().addHeader(
            "Content-disposition",
            String.format("inline; filename=%s_%s.%s",
                Util.encodeURI(doc.getDocumentReference().getLastSpaceReference().getName(), context),
                Util.encodeURI(doc.getDocumentReference().getName(), context), exportType.getExtension()));
        exporter.export(doc, context.getResponse().getOutputStream(), exportType, context);

        return null;
    }

    private String exportXAR(XWikiContext context) throws XWikiException, IOException, WikiStreamException
    {
        XWikiRequest request = context.getRequest();

        boolean history = Boolean.valueOf(request.get("history"));
        boolean backup = Boolean.valueOf(request.get("backup"));
        String author = request.get("author");
        String description = request.get("description");
        String licence = request.get("licence");
        String version = request.get("version");
        String name = request.get("name");
        String[] pages = request.getParameterValues("pages");
        boolean all = ArrayUtils.isEmpty(pages);

        if (!context.getWiki().getRightService().hasWikiAdminRights(context)) {
            context.put("message", "needadminrights");
            return "exception";
        }

        if (name == null) {
            return "export";
        }

        if (StringUtils.isBlank(name)) {
            if (all) {
                name = "backup";
            } else {
                name = "export";
            }
        }

        if (context.getWiki().ParamAsLong("xwiki.action.export.xar.usewikistream", 0) == 1) {
            // Create input wiki stream
            InstanceInputProperties inputProperties = new InstanceInputProperties();

            EntityReferenceSet entities = new EntityReferenceSet();

            if (all) {
                entities.includes(new WikiReference(context.getDatabase()));
            } else {
                if (pages != null) {
                    DocumentReferenceResolver<String> resolver =
                        Utils.getComponent(DocumentReferenceResolver.TYPE_STRING, "current");
                    for (String pageName : pages) {
                        entities.includes(resolver.resolve(pageName));
                    }
                }

                return null;
            }

            inputProperties.setEntities(entities);

            InputWikiStreamFactory inputWikiStreamFactory =
                Utils.getComponent(InputWikiStreamFactory.class, WikiStreamType.XWIKI_INSTANCE.serialize());

            InputWikiStream inputWikiStream = inputWikiStreamFactory.createInputWikiStream(inputProperties);

            // Create output wiki stream
            Map<String, Object> outputProperties = new HashMap<String, Object>();

            XWikiResponse response = context.getResponse();

            outputProperties.put(WikiStreamConstants.PROPERTY_TARGET,
                new DefaultOutputStreamOutputTarget(response.getOutputStream()));
            outputProperties.put("name", name);
            if (description != null) {
                outputProperties.put("description", description);
            }
            if (licence != null) {
                outputProperties.put("license", licence);
            }
            if (author != null) {
                outputProperties.put("author", author);
            }
            if (version != null) {
                outputProperties.put("version", version);
            }
            outputProperties.put("backupPack", backup);
            outputProperties.put("preserveVersion", backup);

            OutputWikiStreamFactory outputWikiStreamFactory =
                Utils.getComponent(OutputWikiStreamFactory.class, WikiStreamType.XWIKI_XAR.serialize());

            OutputWikiStream outputWikiStream = outputWikiStreamFactory.creaOutputWikiStream(outputProperties);

            // Export
            response.setContentType("application/zip");
            response.addHeader("Content-disposition", "attachment; filename=" + Util.encodeURI(name, context) + ".xar");

            inputWikiStream.read(outputWikiStream.getFilter());

            // Flush
            response.getOutputStream().flush();

            // Indicate that we are done with the response so no need to add anything
            context.setFinished(true);
        } else {
            PackageAPI export = ((PackageAPI) context.getWiki().getPluginApi("package", context));
            if (export == null) {
                // No Packaging plugin configured
                return "exception";
            }

            export.setWithVersions(history);

            if (author != null) {
                export.setAuthorName(author);
            }

            if (description != null) {
                export.setDescription(description);
            }

            if (licence != null) {
                export.setLicence(licence);
            }

            if (version != null) {
                export.setVersion(version);
            }

            export.setBackupPack(backup);

            export.setName(name);

            if (all) {
                export.backupWiki();
            } else {
                if (pages != null) {
                    for (int i = 0; i < pages.length; i++) {
                        String pageName = pages[i];
                        String defaultAction = request.get("action_" + pageName);
                        int iAction;
                        try {
                            iAction = Integer.parseInt(defaultAction);
                        } catch (Exception e) {
                            iAction = 0;
                        }
                        export.add(pageName, iAction);
                    }
                }
                export.export();
            }
        }

        return null;
    }
}

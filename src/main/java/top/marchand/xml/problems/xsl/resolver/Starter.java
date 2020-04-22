/**
 * Copyright © 2020, Christophe Marchand, XSpec organization
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package top.marchand.xml.problems.xsl.resolver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.Charset;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.Configuration;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import static net.sf.saxon.s9api.Serializer.Property.*;
import net.sf.saxon.s9api.XdmValue;
import static net.sf.saxon.s9api.XdmValue.makeValue;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltTransformer;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.EntityResolver2;
import org.xmlresolver.Catalog;
import org.xmlresolver.CatalogSource;
import org.xmlresolver.Resolver;
import org.xmlresolver.tools.ResolvingXMLReader;

/**
 *
 * @author cmarchand
 */
public class Starter {
    public static final String CATALOG_NS = "urn:oasis:names:tc:entity:xmlns:xml:catalog";
    public static final String DTD1_PUBLIC = "DTD // PUBLIC // EL1";
    public static final String DTD2_PUBLIC = "DTD // PUBLIC // EL2";
    public static final String DOC1 = "doc1.xml";
    public static final String DOC2 = "doc2.xml";
    public static final String XSL = "merger.xsl";
    public static final QName PARAM_MERGE = new QName("merge");
    public static final QName PARAM_DOCUMENT = new QName("documentToInclude");
    

    public static void main(String[] args) throws Exception {
        Configuration configuration = Configuration.newConfiguration();
        Processor proc = new Processor(configuration);
        File catalogFile = generateCatalog(proc);
        System.out.println("catalog: "+catalogFile.getAbsolutePath());
        Resolver resolver = new Resolver(new Catalog());
        resolver.getCatalog().addSource(new CatalogSource.UriCatalogSource(catalogFile.toURI().toString()));
        
        // show source documents
        displayDoc(DOC1);
        displayDoc(DOC2);
        URL doc1Url = Starter.class.getClassLoader().getResource(DOC1);
        URL doc2Url = Starter.class.getClassLoader().getResource(DOC2);
        XsltCompiler compiler = proc.newXsltCompiler();
        XsltTransformer transformer = compiler.compile(new StreamSource(Starter.class.getClassLoader().getResourceAsStream(XSL))).load();
        transformer.setDestination(proc.newSerializer(System.out));
        // Step One, no merge, only identity on doc1.xml
        System.out.println("Transforming "+DOC1+" with an identity transformer, with default XMLReader");
        try {
            transformer.setSource(new StreamSource(doc1Url.openStream()));
            transformer.transform();
        } catch(SaxonApiException ex) {
            ex.printStackTrace(System.err);
        }
        
        // Step two, set a custom XMLReader to parse source
        System.out.println("\nTransforming "+DOC1+" wwith an identity tranformer, and with a XMLReader with a custom entity resolver");
        XMLReader reader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        reader.setEntityResolver(resolver);
        try {
            transformer.setSource(new SAXSource(reader, new InputSource(doc1Url.openStream())));
            transformer.transform();
        } catch(SaxonApiException ex) {
            ex.printStackTrace(System.err);
        }
        
        // Step three, include DOC2 into DOC1 form XSL
        System.out.println("\nTransforming "+DOC1+" with an XSL that includes "+DOC2+" via a resolve-uri");
        transformer.setURIResolver(resolver);
        transformer.setParameter(PARAM_MERGE, makeValue(Boolean.TRUE));
        try {
            SAXSource source = new SAXSource(reader, new InputSource(doc1Url.openStream()));
            // because we use resolve-uri in XSL
            source.setSystemId(doc1Url.toExternalForm());
            transformer.setSource(source);
            transformer.transform();
        } catch(SaxonApiException ex) {
            ex.printStackTrace(System.err);
        }
        
        System.out.println("\nTransforming "+DOC1+" with and XSL that includes "+DOC2+" using a custom XMLReader class");
        CustomXMLReader.resolver=resolver;
        proc.setConfigurationProperty(Feature.SOURCE_PARSER_CLASS, CustomXMLReader.class.getName());
        // recreating transformer, because processor has been changed
        compiler = proc.newXsltCompiler();
        transformer = compiler.compile(new StreamSource(Starter.class.getClassLoader().getResourceAsStream(XSL))).load();
        transformer.setDestination(proc.newSerializer(System.out));
        transformer.setParameter(PARAM_DOCUMENT, XdmValue.makeValue(doc2Url.toURI()));
        transformer.setParameter(PARAM_MERGE, makeValue(Boolean.TRUE));
        try {
            SAXSource source = new SAXSource(reader, new InputSource(doc1Url.openStream()));
            // because we use resolve-uri in XSL
            source.setSystemId(doc1Url.toExternalForm());
            transformer.setSource(source);
            transformer.transform();
        } catch(SaxonApiException ex) {
            ex.printStackTrace(System.err);
        }
    }
    
    private static File generateCatalog(Processor proc) throws SaxonApiException, IOException, XMLStreamException {
        File catalogFile = File.createTempFile("catalog-", ".xml");
        URL dtd1Url = Starter.class.getClassLoader().getResource("dtd1.dtd");
        URL dtd2Url = Starter.class.getClassLoader().getResource("dtd2.dtd");
        
        try(FileOutputStream fos = new FileOutputStream(catalogFile); OutputStreamWriter osw = new OutputStreamWriter(fos, Charset.forName("UTF-8"))) {
            Serializer serializer = proc.newSerializer(osw);
            serializer.setOutputProperty(INDENT, "true");
            serializer.setOutputProperty(METHOD, "xml");
            XMLStreamWriter xmlWriter = serializer.getXMLStreamWriter();
            xmlWriter.writeStartDocument("UTF-8", "1.0");
            xmlWriter.writeStartElement("catalog");
            xmlWriter.setDefaultNamespace(CATALOG_NS);
            xmlWriter.writeNamespace("", CATALOG_NS);
            xmlWriter.writeEmptyElement(CATALOG_NS, "public");
            xmlWriter.writeAttribute("publicId", DTD1_PUBLIC);
            xmlWriter.writeAttribute("uri", dtd1Url.toString());
            xmlWriter.writeEmptyElement(CATALOG_NS, "public");
            xmlWriter.writeAttribute("publicId", DTD2_PUBLIC);
            xmlWriter.writeAttribute("uri", dtd2Url.toString());
            xmlWriter.writeEndElement();
            xmlWriter.writeEndDocument();
            fos.flush();
        }
        return catalogFile;
    }
    
    private static void displayDoc(String docName) throws IOException {
        PrintStream out = System.out;
        out.println("###########################");
        out.println("# "+docName+" is");
        InputStream is = Starter.class.getClassLoader().getResourceAsStream(docName);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line = br.readLine();
            while(line!=null) {
                out.println(line);
                line = br.readLine();
            }
        }
        out.println("###########################");
    }
    
}

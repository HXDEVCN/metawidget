// Metawidget
//
// This file is dual licensed under both the LGPL
// (http://www.gnu.org/licenses/lgpl-2.1.html) and the EPL
// (http://www.eclipse.org/org/documents/epl-v10.php). As a
// recipient of Metawidget, you may choose to receive it under either
// the LGPL or the EPL.
//
// Commercial licenses are also available. See http://metawidget.org
// for details.

package org.metawidget.website.config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

import junit.framework.TestCase;

import org.metawidget.util.IOUtils;

/**
 * @author <a href="http://kennardconsulting.com">Richard Kennard</a>
 */

public class XmlSchemaGeneratorTaskTest
	extends TestCase {

	//
	// Private members
	//

	private XmlSchemaGeneratorTask	mXmlSchemaGeneratorTask;

	//
	// Public methods
	//

	public void testExecute()
		throws Exception {

		// Run the task

		String jar = "../../modules/java/all/target/metawidget-all.jar";
		String destDir = "target/xsd-test";

		mXmlSchemaGeneratorTask.setJar( jar );
		mXmlSchemaGeneratorTask.setDestDir( destDir );
		mXmlSchemaGeneratorTask.execute();

		// Check the output

		assertTrue( new File( new File( destDir ), "index.php" ).exists() );
		assertTrue( new File( new File( destDir ), "org.metawidget.android.widget-1.0.xsd" ).exists() );
		assertTrue( new File( new File( destDir ), "org.metawidget.faces.component.html-1.0.xsd" ).exists() );
		assertTrue( new File( new File( destDir ), "org.metawidget.widgetbuilder.composite-1.0.xsd" ).exists() );

		// For each file...

		for ( File file : new File( destDir ).listFiles() ) {
			// ...read the contents...

			ByteArrayOutputStream streamOut = new ByteArrayOutputStream();
			IOUtils.streamBetween( new FileInputStream( file ), streamOut );
			String contents = streamOut.toString();

			// ...and test the contents...

			String filename = file.getName();

			if ( "index.php".equals( filename ) ) {
				assertTrue( filename, contents.contains( "<?php $title = 'XML Schemas'; require_once '../include/page-start.php'; ?>\r\n\r\n" ) );
				assertTrue( filename, contents.contains( "\t<?php $floater='xml.png'; require_once '../include/body-start.php'; ?>\r\n\r\n" ) );
				assertTrue( filename, contents.contains( "<h2>XML Schemas</h2>" ) );
				assertTrue( filename, contents.contains( "<h3>Inspection Results</h3>" ) );
				assertTrue( filename, contents.contains( "<h3>External Configuration</h3>" ) );
				assertTrue( filename, contents.contains( "\t<?php require_once '../include/body-end.php'; ?>\r\n\r\n" ) );
				assertTrue( filename, contents.contains( "<?php require_once '../include/page-end.php'; ?>" ) );

				for ( File nestedFile : new File( destDir ).listFiles() ) {
					String nestedFilename = nestedFile.getName();

					if ( "index.php".equals( nestedFilename ) ) {
						continue;
					}

					assertTrue( nestedFilename, contents.contains( "<li><a href=\"" + nestedFilename + "\">" + nestedFilename + "</a></li>" ) );
				}

				continue;
			}

			// ...for things we SHOULD see...

			int indexOf = filename.indexOf( "-" );
			String packageName = filename.substring( 0, indexOf );

			assertTrue( filename, contents.contains( "<?xml version=\"1.0\" ?>" ) );
			assertTrue( filename, contents.contains( "<xs:schema targetNamespace=\"java:" + packageName + "\" xmlns=\"java:" + packageName + "\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" version=\"1.0\">" ) );
			assertTrue( filename, contents.contains( "<xs:complexType>" ) );
			assertTrue( filename, contents.contains( "</xs:complexType>" ) );
			assertTrue( filename, contents.contains( "</xs:schema>" ) );

			// ...and things we SHOULDN'T

			// (don't want to see Java base class properties)

			assertTrue( filename, !contents.contains( "name=\"class\"" ) );

			// (don't want to see SWT Control properties)

			assertTrue( filename, !contents.contains( "name=\"backgroundMode\"" ) );

			// (don't want to see JSP TagSupport properties)

			assertTrue( filename, !contents.contains( "name=\"pageContext\"" ) );

			// ConfigReader isn't configurable

			assertTrue( filename, !contents.contains( "ConfigReader" ) );
		}
	}

	public void testGenerateClassBlock()
		throws Exception {

		// Not concrete

		String schema = mXmlSchemaGeneratorTask.generateClassBlock( "org.metawidget.inspector.impl", "BaseObjectInspector" );
		assertEquals( null, schema );

		// Not immutable

		schema = mXmlSchemaGeneratorTask.generateClassBlock( "java.lang", "String" );
		assertEquals( null, schema );

		// No config

		schema = mXmlSchemaGeneratorTask.generateClassBlock( "org.metawidget.swing.widgetbuilder", "ReadOnlyWidgetBuilder" );

		assertTrue( schema.contains( "\r\n\t<!-- ReadOnlyWidgetBuilder -->\r\n" ) );
		assertTrue( schema.contains( "\t<xs:element name=\"readOnlyWidgetBuilder\">\r\n" ) );
		assertTrue( schema.contains( "\t\t<xs:complexType>\r\n" ) );
		assertTrue( schema.contains( "\t\t\t<xs:attribute name=\"config\" type=\"xs:string\" use=\"prohibited\"/>\r\n" ) );
		assertTrue( schema.contains( "\t\t</xs:complexType>\r\n" ) );
		assertTrue( schema.contains( "\t</xs:element>\r\n" ) );

		schema = mXmlSchemaGeneratorTask.generateClassBlock( "org.metawidget.inspector.propertytype", "PropertyTypeInspector" );

		assertTrue( schema.contains( "\r\n\t<!-- PropertyTypeInspector -->\r\n" ) );
		assertTrue( schema.contains( "\t<xs:element name=\"propertyTypeInspector\">\r\n" ) );
		assertTrue( schema.contains( "\t\t<xs:complexType>\r\n" ) );
		assertTrue( schema.contains( "\t\t\t<xs:all>\r\n" ) );

		assertTrue( schema.contains( "\t\t\t\t<xs:element name=\"actionStyle\" minOccurs=\"0\">\r\n" ) );
		assertTrue( schema.contains( "\t\t\t\t\t<xs:complexType>\r\n" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t<xs:sequence>\r\n" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t\t<xs:any processContents=\"lax\"/>\r\n" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t</xs:sequence>\r\n" ) );
		assertTrue( schema.contains( "\t\t\t\t\t</xs:complexType>\r\n" ) );
		assertTrue( schema.contains( "\t\t\t\t</xs:element>\r\n" ) );

		assertTrue( schema.contains( "\t\t\t\t<xs:element name=\"propertyStyle\" minOccurs=\"0\">\r\n" ) );
		assertTrue( schema.contains( "\t\t\t\t\t<xs:complexType>\r\n" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t<xs:sequence>\r\n" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t\t<xs:any processContents=\"lax\"/>\r\n" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t</xs:sequence>\r\n" ) );
		assertTrue( schema.contains( "\t\t\t\t\t</xs:complexType>\r\n" ) );
		assertTrue( schema.contains( "\t\t\t\t</xs:element>\r\n" ) );

		assertTrue( schema.contains( "\t\t\t</xs:all>\r\n" ) );
		assertTrue( schema.contains( "\t\t\t<xs:attribute name=\"config\" type=\"xs:string\"/>\r\n" ) );
		assertTrue( schema.contains( "\t\t</xs:complexType>\r\n" ) );
		assertTrue( schema.contains( "\t</xs:element>\r\n" ) );

		// Required config

		schema = mXmlSchemaGeneratorTask.generateClassBlock( "org.metawidget.inspector.xml", "XmlInspector" );

		assertTrue( schema.contains( "\r\n\t<!-- XmlInspector -->\r\n" ) );
		assertTrue( schema.contains( "\t<xs:element name=\"xmlInspector\">\r\n" ) );
		assertTrue( schema.contains( "\t\t<xs:complexType>\r\n" ) );
		assertTrue( schema.contains( "\t\t\t<xs:all>\r\n" ) );

		assertTrue( schema.contains( "\t\t\t\t<xs:element name=\"inputStream\" minOccurs=\"0\">" ) );
		assertTrue( schema.contains( "\t\t\t\t\t<xs:complexType>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t<xs:sequence>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t\t<xs:choice>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t\t\t<xs:element name=\"file\" type=\"xs:string\"/>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t\t\t<xs:element name=\"resource\" type=\"xs:string\"/>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t\t\t<xs:element name=\"url\" type=\"xs:anyURI\"/>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t\t</xs:choice>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t</xs:sequence>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t</xs:complexType>" ) );
		assertTrue( schema.contains( "\t\t\t\t</xs:element>" ) );

		assertTrue( schema.contains( "\t\t\t\t<xs:element name=\"inputStreams\" minOccurs=\"0\">" ) );
		assertTrue( schema.contains( "\t\t\t\t\t<xs:complexType>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t<xs:sequence>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t\t<xs:element name=\"array\">" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t\t\t<xs:complexType>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t\t\t\t<xs:sequence>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t\t\t\t\t<xs:any maxOccurs=\"unbounded\" processContents=\"lax\"/>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t\t\t\t</xs:sequence>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t\t\t</xs:complexType>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t\t</xs:element>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t</xs:sequence>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t</xs:complexType>" ) );
		assertTrue( schema.contains( "\t\t\t\t</xs:element>" ) );

		assertTrue( schema.contains( "\t\t\t</xs:all>\r\n" ) );
		assertTrue( schema.contains( "\t\t\t<xs:attribute name=\"config\" type=\"xs:string\" use=\"required\"/>\r\n" ) );
		assertTrue( schema.contains( "\t\t</xs:complexType>\r\n" ) );
		assertTrue( schema.contains( "\t</xs:element>\r\n" ) );

		// maxOccurs="unbounded"

		schema = mXmlSchemaGeneratorTask.generateClassBlock( "org.metawidget.faces.component.html", "HtmlMetawidget" );

		assertTrue( schema.contains( "\r\n\t<!-- HtmlMetawidget -->\r\n" ) );
		assertTrue( schema.contains( "\t<xs:element name=\"htmlMetawidget\">\r\n" ) );
		assertTrue( schema.contains( "\t\t<xs:complexType>\r\n" ) );
		assertTrue( schema.contains( "\t\t\t<xs:sequence>\r\n" ) );

		assertTrue( schema.contains( "\t\t\t\t<xs:element name=\"parameter\" minOccurs=\"0\">" ) );
		assertTrue( schema.contains( "\t\t\t\t\t<xs:complexType>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t<xs:sequence>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t\t<xs:element name=\"string\" type=\"xs:string\"/>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t\t<xs:any processContents=\"lax\"/>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t\t</xs:sequence>" ) );
		assertTrue( schema.contains( "\t\t\t\t\t</xs:complexType>" ) );
		assertTrue( schema.contains( "\t\t\t\t</xs:element>" ) );

		// widgetProcessors should be an array

		assertTrue( schema.contains( "<xs:element name=\"array\">" ) );
		assertTrue( !schema.contains( "<xs:element name=\"list\">" ) );

		assertTrue( schema.contains( "\t\t\t</xs:sequence>\r\n" ) );
		assertTrue( schema.contains( "\t\t</xs:complexType>\r\n" ) );
		assertTrue( schema.contains( "\t</xs:element>\r\n" ) );
	}

	//
	// Protected methods
	//

	@Override
	public void setUp() {

		mXmlSchemaGeneratorTask = new XmlSchemaGeneratorTask();
	}
}
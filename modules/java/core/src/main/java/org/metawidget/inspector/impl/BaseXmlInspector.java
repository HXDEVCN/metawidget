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

package org.metawidget.inspector.impl;

import static org.metawidget.inspector.InspectionResultConstants.*;

import java.io.InputStream;
import java.util.Map;

import org.metawidget.config.iface.ResourceResolver;
import org.metawidget.inspector.iface.DomInspector;
import org.metawidget.inspector.iface.InspectorException;
import org.metawidget.inspector.impl.propertystyle.Property;
import org.metawidget.inspector.impl.propertystyle.PropertyStyle;
import org.metawidget.inspector.impl.propertystyle.ValueAndDeclaredType;
import org.metawidget.util.ArrayUtils;
import org.metawidget.util.ClassUtils;
import org.metawidget.util.LogUtils;
import org.metawidget.util.LogUtils.Log;
import org.metawidget.util.XmlUtils;
import org.metawidget.util.simple.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Convenience implementation for Inspectors that inspect XML files.
 * <p>
 * Handles taking a 'flat' XML file (eg. only one <code>&lt;entity&gt;</code> node deep) and
 * traversing nested paths, such as <code>foo/bar</code>, from...
 * <p>
 * <code>
 * &lt;entity type="foo"&gt;<br/>
 * &nbsp;&nbsp;&nbsp;&lt;property name="myBar" type="bar"&gt;<br/>
 * &lt;/entity&gt;
 * </code>
 * <p>
 * ...to top level...
 * <p>
 * <code>
 * &lt;entity type="bar"&gt;
 * </code>
 * <p>
 * ...and constructing...
 * <p>
 * <code>&lt;entity name="myBar" type="bar"&gt;</code>
 * <p>
 * ...as output.
 * <p>
 * <h2>Schema Validation</h2>
 * <p>
 * This class does not support schema validation - it is not that useful in practice for two
 * reasons. First, Inspectors like <code>HibernateInspector</code> cannot use it because they can be
 * pointed at different kinds of files (eg. hibernate.cfg.xml or hibernate-mapping.hbm.xml). Second,
 * Inspectors that are intended for Android environments (eg. <code>XmlInspector</code>) cannot use
 * it because Android's Dalvik preprocessor balks at the unsupported schema classes (even if they're
 * wrapped in a <code>ClassNotFoundException</code>).
 * <p>
 * <h2>Mixing XML and Object-based Inspectors</h2>
 * <p>
 * Several pieces of functionality apply to mixing XML-based <code>Inspector</code>s (e.g.
 * <code>XmlInspector</code>) and Object-based <code>Inspector</code>s (e.g.
 * <code>PropertyTypeInspector</code>) in the same application (i.e. via
 * <code>CompositeInspector</code>).
 * <p>
 * First, you may encounter a problem whereby the Object-based <code>Inspector</code>s will always
 * stop at <code>null</code> or recursive references, whereas the XML <code>Inspector</code>s (which
 * have no knowledge of Object values) will continue. This can lead to the
 * <code>WidgetBuilder</code>s constructing a UI for a <code>null</code> Object, which may upset
 * some <code>WidgetProcessor</code>s (e.g. <code>BeansBindingProcessor</code>). To resolve this,
 * you can set <code>BaseXmlInspectorConfig.setRestrictAgainstObject</code>, whereby the XML-based
 * <code>Inspector</code> will do a check for <code>null</code> or recursive references, and not
 * return any XML.
 * <p>
 * In addition, setting <code>restrictAgainstObject</code> allows the XML <code>Inspector</code> to
 * traverse child relationships and infer their types using the Object. This saves having to
 * explicitly specify those types and relationships in the XML.
 * <p>
 * Second, by default you need to explicitly specify any inheritance relationships between types in
 * the XML, because the XML has no knowledge of your Java classes. This includes the names of any
 * proxied classes. If this becomes laborious, you can set
 * <code>BaseXmlInspectorConfig.setInferInheritanceHierarchy</code> to infer the relationships
 * automatically from your Java classes. If you are using <code>setRestrictAgainstObject</code>,
 * <code>setInferInheritanceHierarchy</code> is implied.
 * <p>
 * Third, it is important the properties defined by the XML and the ones defined by the Java classes
 * stay in sync. To enforce this, you can set
 * <code>BaseXmlInspectorConfig.setValidateAgainstClasses</code>.
 *
 * @author <a href="http://kennardconsulting.com">Richard Kennard</a>
 */

// TODO: rework BaseXmlInspector to support arbitrary nesting for XML Schemas
// TODO: repackage npm to include front-end technologies

public abstract class BaseXmlInspector
	implements DomInspector<Element> {

	//
	// Protected members
	//

	protected Log				mLog	= LogUtils.getLog( getClass() );

	//
	// Private members
	//

	/**
	 * Shared DOM to store this Inspector's source XML.
	 * <p>
	 * This member is private because, as <a
	 * href="https://issues.apache.org/jira/browse/XERCESJ-727">pointed out here</a>: "There's no
	 * requirement that a DOM be thread safe, so applications need to make sure that threads are
	 * properly synchronized for concurrent access to [a shared] DOM. This is true even if you're
	 * just invoking read operations".
	 */

	private Element				mRoot;

	private final PropertyStyle	mRestrictAgainstObject;

	private final boolean		mInferInheritanceHierarchy;

	//
	// Constructor
	//

	/**
	 * Config-based constructor.
	 * <p>
	 * All BaseXmlInspector inspectors must be configurable, to allow specifying an XML file.
	 */

	protected BaseXmlInspector( BaseXmlInspectorConfig config ) {

		try {
			// Look up the XML file

			InputStream[] inputStreams = config.getInputStreams();

			if ( inputStreams != null && inputStreams.length > 0 ) {
				mRoot = getDocumentElement( config.getResourceResolver(), config.getInputStreams() );
			} else {

				// REFACTOR: support both at once

				Document[] documents = config.getDocuments();

				if ( documents != null && documents.length > 0 ) {
					mRoot = getDocumentElement( documents );
				}
			}

			if ( mRoot == null ) {
				throw InspectorException.newException( "No XML input file specified" );
			}

			// Debug

			if ( mLog.isTraceEnabled() ) {
				mLog.trace( XmlUtils.documentToString( mRoot.getOwnerDocument(), false ) );
			}

			// restrictAgainstObject

			mRestrictAgainstObject = config.getRestrictAgainstObject();

			// inferInheritanceHierarchy

			mInferInheritanceHierarchy = config.isInferInheritanceHierarchy();

			if ( mRestrictAgainstObject != null && mInferInheritanceHierarchy ) {
				throw InspectorException.newException( "When using restrictAgainstObject, inferInheritanceHierarchy is implied" );
			}

			// validateAgainstClasses

			PropertyStyle validateAgainstClasses = config.getValidateAgainstClasses();

			if ( validateAgainstClasses != null ) {

				String topLevelTypeAttribute = getTopLevelTypeAttribute();
				String extendsAttribute = getExtendsAttribute();
				String nameAttribute = getNameAttribute();
				String typeAttribute = getTypeAttribute();

				// For each entity...

				Element entity = XmlUtils.getChildWithAttribute( mRoot, topLevelTypeAttribute );

				while ( entity != null ) {

					// ...the maps to a Java class...

					String topLevelType = entity.getAttribute( topLevelTypeAttribute );
					Class<?> actualClass = ClassUtils.niceForName( topLevelType );

					if ( actualClass != null ) {

						// ...check its extends...

						String extendz = entity.getAttribute( extendsAttribute );
						Class<?> actualSuperclass = actualClass.getSuperclass();

						if ( !"".equals( extendz ) && !extendz.equals( actualSuperclass.getName() ) ) {
							throw InspectorException.newException( actualClass + " extends " + actualSuperclass + ", not '" + extendz + "'" );
						}

						// ...then for each property...

						Map<String, Property> actualProperties = validateAgainstClasses.getProperties( topLevelType );
						Element property = XmlUtils.getChildWithAttribute( entity, nameAttribute );

						while ( property != null ) {

							// ...check it exists

							String propertyName = property.getAttribute( nameAttribute );
							Property actualProperty = actualProperties.get( propertyName );

							if ( actualProperty == null ) {
								throw InspectorException.newException( actualClass + " does not define a property '" + propertyName + "'" );
							}

							String propertyType = property.getAttribute( typeAttribute );
							String actualType = actualProperty.getType();

							if ( !"".equals( propertyType ) && !propertyType.equals( actualType ) ) {
								throw InspectorException.newException( actualClass + " defines property '" + propertyName + "' to be " + actualType + ", not '" + propertyType + "'" );
							}

							property = XmlUtils.getSiblingWithAttribute( property, nameAttribute );
						}
					}

					entity = XmlUtils.getSiblingWithAttribute( entity, topLevelTypeAttribute );
				}
			}

		} catch ( Exception e ) {
			throw InspectorException.newException( e );
		}
	}

	//
	// Public methods
	//

	/**
	 * Inspect the given Object according to the given path, and return the result as a String
	 * conforming to inspection-result-1.0.xsd.
	 * <p>
	 * This method is marked <code>final</code> because most Metawidget implementations will call
	 * <code>inspectAsDom</code> directly instead.
	 */

	public final String inspect( Object toInspect, String type, String... names ) {

		Element element = inspectAsDom( toInspect, type, names );

		if ( element == null ) {
			return null;
		}

		return XmlUtils.nodeToString( element, false );
	}

	public Element inspectAsDom( Object toInspect, String type, String... names ) {

		// If no type, return nothing

		if ( type == null ) {
			return null;
		}

		try {
			Document document;
			Element entity;
			ValueAndDeclaredType valueAndDeclaredType;
			Map<String, String> parentAttributes = null;

			// "There's no requirement that a DOM be thread safe, so applications need to make sure
			// that threads are properly synchronized for concurrent access to [a shared] DOM. This
			// is true even if you're just invoking read operations"
			//
			// https://issues.apache.org/jira/browse/XERCESJ-727

			synchronized ( mRoot ) {

				// If the path has a parent...

				if ( names != null && names.length > 0 ) {
					// ...inspect its property for useful attributes...

					Element propertyInParent = (Element) traverse( toInspect, type, true, names ).getValue();

					if ( propertyInParent != null ) {
						parentAttributes = inspectProperty( propertyInParent );
					}
				}

				// ...otherwise, just start at the end point

				valueAndDeclaredType = traverse( toInspect, type, false, names );

				if ( valueAndDeclaredType.getValue() == null ) {

					if ( parentAttributes == null || parentAttributes.isEmpty() ) {
						return null;
					}

					document = XmlUtils.newDocument();
					entity = document.createElementNS( NAMESPACE, ENTITY );

				} else {

					// Inspect traits

					document = XmlUtils.newDocument();
					entity = document.createElementNS( NAMESPACE, ENTITY );
					inspectTraits( (Element) valueAndDeclaredType.getValue(), entity );

					// Nothing of consequence to return?

					if ( !entity.hasChildNodes() && entity.getAttributes().getLength() == 0 && parentAttributes == null ) {
						return null;
					}
				}
			}

			Element root = document.createElementNS( NAMESPACE, ROOT );
			root.setAttribute( VERSION, "1.0" );
			document.appendChild( root );
			root.appendChild( entity );

			// Add parent attributes (if any)

			XmlUtils.setMapAsAttributes( entity, parentAttributes );

			// Use the declared type so as to align with other Inspectors

			if ( valueAndDeclaredType.getDeclaredType() != null ) {
				entity.setAttribute( TYPE, valueAndDeclaredType.getDeclaredType() );
			}

			// Return the root

			return root;
		} catch ( Exception e ) {
			throw InspectorException.newException( e );
		}
	}

	//
	// Protected methods
	//

	/**
	 * Parse the given InputStreams into a single DOM Document, and return its root.
	 *
	 * @param resolver
	 *            helper in case <code>getDocumentElement</code> needs to resolve references defined
	 *            in the <code>InputStream</code>.
	 */

	protected Element getDocumentElement( ResourceResolver resolver, InputStream... files )
		throws Exception {

		int length = files.length;
		Document[] documents = new Document[length];

		for ( int loop = 0; loop < length; loop++ ) {
			documents[loop] = XmlUtils.parse( files[loop] );
		}

		return getDocumentElement( documents );
	}

	/**
	 * Merge the given DOM Documents into a single Document, and return its root.
	 */

	protected Element getDocumentElement( Document... documents )
		throws Exception {

		Document documentMaster = null;

		for ( Document document : documents ) {

			if ( !document.hasChildNodes() ) {
				continue;
			}

			preprocessDocument( document );

			if ( documentMaster == null || !documentMaster.hasChildNodes() ) {
				documentMaster = document;
				continue;
			}

			XmlUtils.combineElements( documentMaster.getDocumentElement(), document.getDocumentElement(), getTopLevelTypeAttribute(), getNameAttribute() );
		}

		if ( documentMaster == null ) {
			return null;
		}

		return documentMaster.getDocumentElement();
	}

	/**
	 * Hook for subclasses to preprocess the document after the Inspector is initialized.
	 * <p>
	 * For example, <code>HibernateInspector</code> preprocesses the class names in Hibernate
	 * mapping files to make them fully qualified.
	 *
	 * @param document
	 *            DOM of XML being processed
	 */

	protected void preprocessDocument( Document document ) {

		// Do nothing by default
	}

	/**
	 * Inspect the <code>toInspect</code> for properties and actions.
	 * <p>
	 * This method can be overridden by clients wishing to modify the inspection process. Most
	 * clients will find it easier to override one of the sub-methods, such as
	 * <code>inspectTrait</code> or <code>inspectProperty</code>.
	 */

	protected void inspectTraits( Element toInspect, Element toAddTo ) {

		if ( toInspect == null ) {
			return;
		}

		Document document = toAddTo.getOwnerDocument();

		// Do 'extends' attribute first

		String extendsAttribute = getExtendsAttribute();

		if ( extendsAttribute != null ) {
			if ( toInspect.hasAttribute( extendsAttribute ) ) {
				inspectTraits( (Element) traverse( null, toInspect.getAttribute( extendsAttribute ), false ).getValue(), toAddTo );
			}
		}

		// Next, inspect each child...

		Element element = document.createElementNS( NAMESPACE, ENTITY );
		inspectTraitSiblings( element, XmlUtils.getFirstChildElement( toInspect ) );

		// ...and combine them all. Note the element may already exist from the superclass,
		// and its attributes will get overridden by the subclass

		XmlUtils.combineElements( toAddTo, element, NAME, NAME );
	}

	/**
	 * Inspect the given trait, looping over its siblings.
	 * <p>
	 * This method can be overridden by clients wishing to modify the inspection process. Most
	 * clients will find it easier to override one of the sub-methods, such as
	 * <code>inspectTrait</code> or <code>inspectProperty</code>.
	 */

	protected void inspectTraitSiblings( Element toAddTo, Element toInspect ) {

		Element toInspectToUse = toInspect;

		while ( toInspectToUse != null ) {

			Element inspectedTrait = inspectTrait( toAddTo.getOwnerDocument(), toInspectToUse );

			if ( inspectedTrait != null ) {
				toAddTo.appendChild( inspectedTrait );
			}

			toInspectToUse = XmlUtils.getNextSiblingElement( toInspectToUse );
		}
	}

	/**
	 * Inspect the given Element and return a Map of attributes if it is a trait.
	 * <p>
	 * It is this method's responsibility to decide whether the given Element does, in fact, qualify
	 * as a 'trait' - based on its own rules.
	 *
	 * @param toInspect
	 *            DOM element to inspect
	 */

	protected Element inspectTrait( Document toAddTo, Element toInspect ) {

		// Properties

		Map<String, String> propertyAttributes = inspectProperty( toInspect );

		if ( propertyAttributes != null && !propertyAttributes.isEmpty() ) {
			Element child = toAddTo.createElementNS( NAMESPACE, PROPERTY );
			XmlUtils.setMapAsAttributes( child, propertyAttributes );

			return child;
		}

		// Actions

		Map<String, String> actionAttributes = inspectAction( toInspect );

		if ( actionAttributes != null && !actionAttributes.isEmpty() ) {
			// Sanity check

			if ( propertyAttributes != null ) {
				throw InspectorException.newException( "Ambigious match: " + toInspect.getNodeName() + " matches as both a property and an action" );
			}

			Element child = toAddTo.createElementNS( NAMESPACE, ACTION );
			XmlUtils.setMapAsAttributes( child, actionAttributes );

			return child;
		}

		return null;
	}

	/**
	 * Inspect the given Element and return a Map of attributes if it is a property.
	 * <p>
	 * It is this method's responsibility to decide whether the given Element does, in fact, qualify
	 * as a 'property' - based on its own rules. Does nothing by default.
	 *
	 * @param toInspect
	 *            DOM element to inspect
	 * @return a Map of the property's attributes, or null if this Element is not a property
	 */

	protected Map<String, String> inspectProperty( Element toInspect ) {

		return null;
	}

	/**
	 * Inspect the given Element and return a Map of attributes if it is an action.
	 * <p>
	 * It is this method's responsibility to decide whether the given Element does, in fact, qualify
	 * as an 'action' - based on its own rules. Does nothing by default.
	 *
	 * @param toInspect
	 *            DOM element to inspect
	 * @return a Map of the property's attributes, or null if this Element is not an action
	 */

	protected Map<String, String> inspectAction( Element toInspect ) {

		return null;
	}

	/**
	 * @return the Element (may be null) and its declared type (not actual type). Never null.
	 *         If the declared type within the ValueAndDeclaredType is null, inspection will be
	 *         aborted
	 */

	protected ValueAndDeclaredType traverse( Object toTraverse, String type, boolean onlyToParent, String... names ) {

		// If given a non-null Object, use it to restrictAgainstObject

		String typeToInspect = type;
		String[] namesToInspect = names;
		Object traverseAgainstObject = null;
		String declaredType = null;

		if ( toTraverse != null && mRestrictAgainstObject != null ) {
			ValueAndDeclaredType valueAndDeclaredType = mRestrictAgainstObject.traverse( toTraverse, typeToInspect, onlyToParent, namesToInspect );
			traverseAgainstObject = valueAndDeclaredType.getValue();

			if ( valueAndDeclaredType.getDeclaredType() != null ) {
				declaredType = valueAndDeclaredType.getDeclaredType();
			}

			if ( traverseAgainstObject == null ) {
				return new ValueAndDeclaredType( null, declaredType );
			}

			if ( onlyToParent ) {
				namesToInspect = new String[] { namesToInspect[namesToInspect.length - 1] };
			} else {
				namesToInspect = null;
			}

			typeToInspect = traverseAgainstObject.getClass().getName();
		}

		if ( declaredType == null ) {
			declaredType = typeToInspect;
		}

		// Validate type

		String topLevelTypeAttribute = getTopLevelTypeAttribute();
		Element topLevelElement = XmlUtils.getChildWithAttributeValue( mRoot, topLevelTypeAttribute, typeToInspect );

		if ( topLevelElement == null ) {

			if ( traverseAgainstObject == null && !mInferInheritanceHierarchy ) {
				return new ValueAndDeclaredType( null, declaredType );
			}

			// If using mRestrictAgainstObject or mInferInheritanceHierarchy, attempt to match
			// superclasses by checking against the Java class heirarchy

			Class<?> actualClass;

			if ( traverseAgainstObject != null ) {
				actualClass = traverseAgainstObject.getClass();
			} else {
				actualClass = ClassUtils.niceForName( typeToInspect );

				if ( actualClass == null ) {
					return new ValueAndDeclaredType( null, typeToInspect );
				}
			}

			while ( topLevelElement == null ) {

				actualClass = actualClass.getSuperclass();

				if ( actualClass == null ) {
					break;
				}

				topLevelElement = XmlUtils.getChildWithAttributeValue( mRoot, topLevelTypeAttribute, actualClass.getName() );
			}

			if ( topLevelElement == null ) {
				return new ValueAndDeclaredType( null, declaredType );
			}
		}

		Element elementWithNamedChildren = traverseFromTopLevelTypeToNamedChildren( topLevelElement );

		if ( namesToInspect == null || elementWithNamedChildren == null ) {
			return new ValueAndDeclaredType( elementWithNamedChildren, declaredType );
		}

		int length = namesToInspect.length;

		if ( length == 0 ) {
			return new ValueAndDeclaredType( elementWithNamedChildren, declaredType );
		}

		// Traverse names

		String extendsAttribute = getExtendsAttribute();
		String nameAttribute = getNameAttribute();
		String typeAttribute = getTypeAttribute();
		String referenceAttribute = getReferenceAttribute();

		// For each name...

		for ( int loop = 0; loop < length; loop++ ) {
			String name = namesToInspect[loop];
			declaredType = null;

			// ...find the property with that name

			Element property = XmlUtils.getChildWithAttributeValue( elementWithNamedChildren, nameAttribute, name );

			// If none, XML structure may support 'extends', so jump across to the extended element
			// and search for named properties there

			if ( property == null && extendsAttribute != null ) {

				while ( true ) {

					// ('extends' may be several levels deep)

					if ( !elementWithNamedChildren.hasAttribute( extendsAttribute ) ) {
						break;
					}

					String childExtends = elementWithNamedChildren.getAttribute( extendsAttribute );
					elementWithNamedChildren = XmlUtils.getChildWithAttributeValue( mRoot, topLevelTypeAttribute, childExtends );

					if ( elementWithNamedChildren == null ) {
						break;
					}

					property = XmlUtils.getChildWithAttributeValue( elementWithNamedChildren, nameAttribute, name );

					if ( property != null ) {
						break;
					}
				}
			}

			// If still none, XML structure may support 'reference', so search for referenced
			// properties

			if ( property == null && referenceAttribute != null ) {

				property = XmlUtils.getChildWithAttributeValue( elementWithNamedChildren, referenceAttribute, name );

				if ( property == null ) {
					return new ValueAndDeclaredType( null, null );
				}

				// Traverse to new top-level element of the given declaredType

				declaredType = name;
			}

			// If still none, give up

			if ( property == null ) {
				return new ValueAndDeclaredType( null, null );
			}

			if ( onlyToParent && loop >= ( length - 1 ) ) {
				return new ValueAndDeclaredType( property, declaredType );
			}

			if ( declaredType == null ) {
				// Fetch typeAttribute (if any)

				if ( property.hasAttribute( typeAttribute ) ) {
					declaredType = property.getAttribute( typeAttribute );
				}

				// Support nested elements with named children (with or without a typeAttribute)

				elementWithNamedChildren = traverseFromTopLevelTypeToNamedChildren( property );

				if ( XmlUtils.getChildWithAttribute( elementWithNamedChildren, nameAttribute ) != null ) {
					continue;
				}

				// If no typeAttribute, support referenceAttribute (though typeAttribute takes
				// precedence)

				if ( !property.hasAttribute( typeAttribute ) ) {

					if ( referenceAttribute == null || XmlUtils.getChildWithAttribute( elementWithNamedChildren, referenceAttribute ) == null ) {
						throw InspectorException.newException( "Property " + name + " in entity " + topLevelElement.getAttribute( typeAttribute ) + " has no @" + typeAttribute + " attribute in the XML, so cannot navigate to " + type + ArrayUtils.toString( namesToInspect, StringUtils.SEPARATOR_FORWARD_SLASH, true, false ) );
					}

					continue;
				}
			}

			// Traverse to new top-level element of the given declaredType

			topLevelElement = XmlUtils.getChildWithAttributeValue( mRoot, topLevelTypeAttribute, declaredType );

			if ( topLevelElement == null ) {
				return new ValueAndDeclaredType( null, declaredType );
			}

			// For ref lookups, topLevelElement may have an additional typeAttribute that is
			// different from topLevelTypeAttribute

			if ( topLevelElement.hasAttribute( typeAttribute ) ) {
				declaredType = topLevelElement.getAttribute( typeAttribute );
			}

			elementWithNamedChildren = traverseFromTopLevelTypeToNamedChildren( topLevelElement );

			if ( elementWithNamedChildren == null ) {
				return new ValueAndDeclaredType( null, declaredType );
			}
		}

		return new ValueAndDeclaredType( elementWithNamedChildren, declaredType );
	}

	/**
	 * The attribute on top-level elements that uniquely identifies them.
	 */

	protected String getTopLevelTypeAttribute() {

		return TYPE;
	}

	/**
	 * The attribute on child elements that uniquely identifies them.
	 */

	protected String getNameAttribute() {

		return NAME;
	}

	/**
	 * The attribute on child elements that identifies another top-level element.
	 * <p>
	 * This is necessary for path traversal. If an XML format does not specify a way to traverse
	 * from a child to another top-level element, the Inspector cannot find information along paths
	 * (eg. <code>foo/bar/baz</code>). There <em>is</em> a way around this but, on balance, we
	 * decided against it (see http://blog.kennardconsulting.com/2008/01/ask-your-father.html).
	 */

	protected String getTypeAttribute() {

		return TYPE;
	}

	/**
	 * The attribute on top-level elements that identifies a superclass relationship (if any).
	 */

	protected String getExtendsAttribute() {

		return null;
	}

	/**
	 * The attribute on child elements that identifies a reference to another element (if any).
	 * Note that <code>typeAttribute</code> will always take precedence over
	 * <code>referenceAttribute</code>.
	 */

	protected String getReferenceAttribute() {

		return null;
	}

	/**
	 * Traverse from the given top-level element (as per <code>getTopLevelTypeAttribute</code>) to
	 * the element which contains named children (as per <code>getNameAttribute</code>). In many
	 * cases this is one and the same, so by default this method simply returns the given element.
	 * <p>
	 * Subclasses can override this method if they need to do some intermediate traversal.
	 *
	 * @return the element containing named children, or null if no such element
	 */

	protected Element traverseFromTopLevelTypeToNamedChildren( Element topLevel ) {

		return topLevel;
	}
}

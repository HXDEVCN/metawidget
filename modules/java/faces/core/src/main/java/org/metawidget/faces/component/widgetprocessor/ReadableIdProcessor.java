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

package org.metawidget.faces.component.widgetprocessor;

import static org.metawidget.inspector.InspectionResultConstants.*;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.component.ActionSource;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.el.MethodBinding;
import javax.faces.el.ValueBinding;

import org.metawidget.faces.FacesUtils;
import org.metawidget.faces.component.UIMetawidget;
import org.metawidget.faces.component.UIStub;
import org.metawidget.util.CollectionUtils;
import org.metawidget.util.simple.StringUtils;
import org.metawidget.widgetprocessor.iface.AdvancedWidgetProcessor;

/**
 * WidgetProcessor to set 'human readable' ids on a UIComponent.
 * <p>
 * Unlike <code>UIViewRoot.createUniqueId</code>, tries to make the id human readable, both for
 * debugging purposes and for when running unit tests (using, say, WebTest). Because the ids are
 * based off the value binding (or method binding) of the UIComponent, this WidgetProcessor must
 * come after <code>StandardBindingProcessor</code> (or equivalent).
 * <p>
 * Clients can plug in a different WidgetProcessor to use <code>UIViewRoot.createUniqueId</code> if
 * preferred. They can even plug in assigning a changing, random id to a component each time it is
 * generated. This is a great way to fox hackers who are trying to POST back pre-generated payloads
 * of HTTP fields (ie. CSRF attacks).
 *
 * @author <a href="http://kennardconsulting.com">Richard Kennard</a>
 */

@SuppressWarnings( "deprecation" )
public class ReadableIdProcessor
	implements AdvancedWidgetProcessor<UIComponent, UIMetawidget> {

	//
	// Public methods
	//

	public void onStartBuild( UIMetawidget metawidget ) {

		metawidget.putClientProperty( ReadableIdProcessor.class, null );
	}

	public UIComponent processWidget( UIComponent component, String elementName, Map<String, String> attributes, UIMetawidget metawidget ) {

		// Does widget need an id?
		//
		// Note: it is very dangerous to reassign an id if the widget already has one,
		// as it will create duplicates in the child component list

		if ( component.getId() != null ) {
			return component;
		}

		// Base action ids on the methodBinding

		if ( ACTION.equals( elementName ) ) {
			MethodBinding methodBinding = ( (ActionSource) component ).getAction();

			if ( methodBinding != null ) {
				setUniqueId( component, methodBinding.getExpressionString(), metawidget );
			} else {
				component.setId( FacesUtils.createUniqueId() );
			}
		} else {
			// Base property ids on the valueBinding

			ValueBinding valueBinding = component.getValueBinding( "value" );

			if ( valueBinding != null ) {
				setUniqueId( component, valueBinding.getExpressionString(), metawidget );
			} else {
				component.setId( FacesUtils.createUniqueId() );
			}
		}

		return component;
	}

	public void onEndBuild( UIMetawidget metawidget ) {

		// Do nothing
	}

	//
	// Protected methods
	//

	protected void setUniqueId( UIComponent component, String expressionString, UIMetawidget metawidget ) {

		String id = StringUtils.camelCase( FacesUtils.unwrapExpression( expressionString ), StringUtils.SEPARATOR_DOT_CHAR );
		id = id.replace( '[', '_' ).replace( ']', '_' );
		setUniqueId( id, component, metawidget );
	}

	protected void setUniqueId( String id, UIComponent component, UIMetawidget metawidget ) {

		String originalId = id;

		// Suffix nested Metawidgets/Stubs, because otherwise if they only expand to a single child
		// they will give that child component a '_2' suffixed id

		if ( component instanceof UIMetawidget ) {
			originalId += "_Metawidget";
		}

		// Convert to an actual, valid id (avoid conflicts)

		Set<String> clientIds = getClientIds( metawidget );
		String nonDuplicateId = originalId;
		int suffix = 1;

		while ( true ) {
			if ( clientIds.add( nonDuplicateId ) ) {
				break;
			}

			suffix++;
			nonDuplicateId = originalId + '_' + suffix;
		}

		// Support stubs

		if ( component instanceof UIStub ) {
			List<UIComponent> children = component.getChildren();

			if ( !children.isEmpty() ) {
				int childSuffix = 1;

				for ( UIComponent childComponent : children ) {
					// Does widget need an id?
					//
					// Note: it is very dangerous to reassign an id if the widget already has one,
					// as it will create duplicates in the child component list

					if ( childComponent.getId() != null ) {
						continue;
					}

					// Give the first Stub component the same id as the original. This is 'cleaner'
					// as the Stub's id never makes it to the output HTML

					if ( childSuffix > 1 ) {
						childComponent.setId( nonDuplicateId + '_' + childSuffix );
					} else {
						childComponent.setId( nonDuplicateId );
					}

					childSuffix++;
				}
			}

			// Still important to set the Stub's id to avoid JSF warnings

			component.setId( FacesUtils.createUniqueId() );
			return;
		}

		// Set Id

		component.setId( nonDuplicateId );
	}

	//
	// Private methods
	//

	/**
	 * Gets client ids of existing children, so as to avoid naming clashes.
	 */

	private Set<String> getClientIds( UIMetawidget metawidget ) {

		Set<String> clientIds = metawidget.getClientProperty( ReadableIdProcessor.class );

		if ( clientIds == null ) {
			// (cache in the metawidget because this could be expensive)

			clientIds = CollectionUtils.newHashSet();
			metawidget.putClientProperty( ReadableIdProcessor.class, clientIds );

			getClientIds( FacesContext.getCurrentInstance().getViewRoot(), clientIds );
		}

		return clientIds;
	}

	private void getClientIds( UIComponent component, Set<String> clientIds ) {

		for ( Iterator<UIComponent> i = component.getFacetsAndChildren(); i.hasNext(); ) {
			UIComponent childComponent = i.next();
			String id = childComponent.getId();

			if ( id != null ) {
				clientIds.add( id );
			}

			getClientIds( childComponent, clientIds );
		}
	}
}

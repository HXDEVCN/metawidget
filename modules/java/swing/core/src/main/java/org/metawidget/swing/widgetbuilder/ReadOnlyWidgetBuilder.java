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

package org.metawidget.swing.widgetbuilder;

import static org.metawidget.inspector.InspectionResultConstants.*;

import java.awt.Component;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.metawidget.swing.Stub;
import org.metawidget.swing.SwingMetawidget;
import org.metawidget.swing.SwingValuePropertyProvider;
import org.metawidget.util.CollectionUtils;
import org.metawidget.util.WidgetBuilderUtils;
import org.metawidget.widgetbuilder.iface.WidgetBuilder;

/**
 * WidgetBuilder for Swing environments.
 * <p>
 * Creates native Swing read-only <code>JComponents</code>, such as <code>JLabels</code>, to suit
 * the inspected fields.
 *
 * @author <a href="http://kennardconsulting.com">Richard Kennard</a>
 */

public class ReadOnlyWidgetBuilder
	implements WidgetBuilder<JComponent, SwingMetawidget>, SwingValuePropertyProvider {

	//
	// Public methods
	//

	public String getValueProperty( Component component ) {

		if ( component instanceof JLabel ) {
			return "text";
		}

		return null;
	}

	public JComponent buildWidget( String elementName, Map<String, String> attributes, SwingMetawidget metawidget ) {

		// Not read-only?

		if ( !WidgetBuilderUtils.isReadOnly( attributes ) ) {
			return null;
		}

		// Hidden

		if ( TRUE.equals( attributes.get( HIDDEN ) ) ) {
			return new Stub();
		}

		// Action

		if ( ACTION.equals( elementName ) ) {
			JButton button = new JButton( metawidget.getLabelString( attributes ) );
			button.setEnabled( false );

			return button;
		}

		// Masked (return a JPanel, so that we DO still render a label)

		if ( TRUE.equals( attributes.get( MASKED ) ) ) {
			return new JPanel();
		}

		// Lookups

		String lookup = attributes.get( LOOKUP );

		if ( lookup != null && !"".equals( lookup ) ) {
			return new JLabel();
		}

		// Lookup the Class

		Class<?> clazz = WidgetBuilderUtils.getActualClassOrType( attributes, String.class );

		if ( clazz != null ) {
			// Primitives

			if ( clazz.isPrimitive() ) {
				return new JLabel();
			}

			if ( String.class.equals( clazz ) ) {
				if ( TRUE.equals( attributes.get( LARGE ) ) ) {
					// Do not use a JLabel: JLabels do not support carriage returns like JTextAreas
					// do, so a multi-line JTextArea formats to a single line JLabel. Instead use
					// a non-editable JTextArea within a borderless JScrollPane

					JTextArea textarea = new JTextArea();

					// Since we know we are dealing with Strings, we consider
					// word-wrapping a sensible default

					textarea.setLineWrap( true );
					textarea.setWrapStyleWord( true );
					textarea.setEditable( false );

					// We also consider 2 rows a sensible default, so that the
					// read-only JTextArea is always distinguishable from a JLabel

					textarea.setRows( 2 );
					JScrollPane scrollPane = new JScrollPane( textarea );
					scrollPane.setBorder( null );

					return scrollPane;
				}

				return new JLabel();
			}

			if ( Character.class.equals( clazz ) ) {
				return new JLabel();
			}

			if ( Date.class.equals( clazz ) ) {
				return new JLabel();
			}

			if ( Boolean.class.equals( clazz ) ) {
				return new JLabel();
			}

			if ( Number.class.isAssignableFrom( clazz ) ) {
				return new JLabel();
			}

			// Collections

			if ( Collection.class.isAssignableFrom( clazz ) ) {
				return new Stub();
			}
		}

		// Not simple, but don't expand

		if ( TRUE.equals( attributes.get( DONT_EXPAND ) ) ) {
			return new JLabel();
		}

		// Nested Metawidget

		return null;
	}
}

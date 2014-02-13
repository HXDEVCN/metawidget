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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.BasicComboPopup;

import junit.framework.TestCase;

import org.metawidget.inspector.annotation.UiAction;
import org.metawidget.inspector.annotation.UiHidden;
import org.metawidget.swing.SwingMetawidget;
import org.metawidget.swing.widgetprocessor.binding.BindingConverter;
import org.metawidget.util.CollectionUtils;
import org.metawidget.util.MetawidgetTestUtils;
import org.metawidget.widgetprocessor.iface.WidgetProcessor;

/**
 * @author <a href="http://kennardconsulting.com">Richard Kennard</a>
 */

public class SwingWidgetBuilderTest
	extends TestCase {

	//
	// Public methods
	//

	public void testWidgetBuilder()
		throws Exception {

		SwingWidgetBuilder widgetBuilder = new SwingWidgetBuilder();
		Map<String, String> attributes = CollectionUtils.newHashMap();

		// JSlider

		attributes.put( TYPE, int.class.getName() );
		attributes.put( MINIMUM_VALUE, "2" );
		attributes.put( MAXIMUM_VALUE, "99" );

		JSlider slider = (JSlider) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertEquals( 2, slider.getMinimum() );
		assertEquals( 2, slider.getValue() );
		assertEquals( 99, slider.getMaximum() );

		try {
			attributes.put( MINIMUM_VALUE, "1.5" );
			widgetBuilder.buildWidget( PROPERTY, attributes, null );
			fail();
		} catch ( NumberFormatException e ) {
			assertEquals( "For input string: \"1.5\"", e.getMessage() );
		}

		// JTextArea

		attributes.put( TYPE, String.class.getName() );
		attributes.put( LARGE, TRUE );

		JScrollPane scrollPane = (JScrollPane) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertTrue( null != scrollPane.getBorder() );
		JTextArea textarea = (JTextArea) scrollPane.getViewport().getView();
		assertEquals( true, textarea.getLineWrap() );
		assertEquals( true, textarea.getWrapStyleWord() );
		assertEquals( true, textarea.isEditable() );
		assertEquals( 2, textarea.getRows() );

		// JComboBox

		attributes.remove( LARGE );
		attributes.put( LOOKUP, "FOO, BAR, BAZ" );

		SwingMetawidget metawidget = new SwingMetawidget();
		JComboBox comboBox = (JComboBox) widgetBuilder.buildWidget( PROPERTY, attributes, metawidget );
		assertEquals( null, comboBox.getItemAt( 0 ) );
		assertEquals( "FOO", comboBox.getItemAt( 1 ) );
		assertEquals( "BAR", comboBox.getItemAt( 2 ) );
		assertEquals( "BAZ", comboBox.getItemAt( 3 ) );
		assertEquals( 4, comboBox.getItemCount() );
		assertTrue( !comboBox.getEditor().getClass().getName().contains( "LookupComboBoxEditor" ) );
		assertTrue( !comboBox.getRenderer().getClass().getName().contains( "LookupComboBoxRenderer" ) );

		// JComboBox with a Converter

		MockBinding mockBinding = new MockBinding();
		metawidget.addWidgetProcessor( mockBinding );
		comboBox = (JComboBox) widgetBuilder.buildWidget( PROPERTY, attributes, metawidget );
		assertEquals( null, comboBox.getItemAt( 0 ) );
		assertEquals( MockEnum.FOO, comboBox.getItemAt( 1 ) );
		assertEquals( MockEnum.BAR, comboBox.getItemAt( 2 ) );
		assertEquals( MockEnum.BAZ, comboBox.getItemAt( 3 ) );
		assertEquals( 4, comboBox.getItemCount() );
		assertTrue( !comboBox.getEditor().getClass().getName().contains( "LookupComboBoxEditor" ) );
		assertTrue( !comboBox.getRenderer().getClass().getName().contains( "LookupComboBoxRenderer" ) );

		// JComboBox with a Converter and labels

		attributes.put( LOOKUP_LABELS, "Foo Label, Bar Label, Baz Label" );
		comboBox = (JComboBox) widgetBuilder.buildWidget( PROPERTY, attributes, metawidget );
		assertTrue( comboBox.getEditor().getClass().getName().contains( "LookupComboBoxEditor" ) );
		assertTrue( comboBox.getRenderer().getClass().getName().contains( "LookupComboBoxRenderer" ) );

		JList popupList = ( (BasicComboPopup) comboBox.getUI().getAccessibleChild( comboBox, 0 ) ).getList();
		assertEquals( "", ( (JLabel) comboBox.getRenderer().getListCellRendererComponent( popupList, null, 0, false, false ) ).getText() );
		assertEquals( "FOO", ( (JLabel) comboBox.getRenderer().getListCellRendererComponent( popupList, "FOO", 1, false, false ) ).getText() );
		assertEquals( "Foo Label", ( (JLabel) comboBox.getRenderer().getListCellRendererComponent( popupList, MockEnum.FOO, 1, false, false ) ).getText() );
		assertEquals( "Bar Label", ( (JLabel) comboBox.getRenderer().getListCellRendererComponent( popupList, MockEnum.BAR, 2, false, false ) ).getText() );
		assertEquals( "Baz Label", ( (JLabel) comboBox.getRenderer().getListCellRendererComponent( popupList, MockEnum.BAZ, 3, false, false ) ).getText() );

		BasicComboBoxEditor editor = (BasicComboBoxEditor) comboBox.getEditor();
		editor.setItem( null );
		assertEquals( "", editor.getItem() );
		editor.setItem( "FOO" );
		assertEquals( "", editor.getItem() );
		editor.setItem( MockEnum.FOO );
		assertEquals( "Foo Label", editor.getItem() );
		editor.setItem( MockEnum.BAR );
		assertEquals( "Bar Label", editor.getItem() );
		editor.setItem( MockEnum.BAZ );
		assertEquals( "Baz Label", editor.getItem() );

		// JComboBox with no Converter but labels

		metawidget.removeWidgetProcessor( mockBinding );

		attributes.put( LOOKUP_LABELS, "Foo Label, Bar Label, Baz Label" );
		comboBox = (JComboBox) widgetBuilder.buildWidget( PROPERTY, attributes, metawidget );
		assertTrue( comboBox.getEditor().getClass().getName().contains( "LookupComboBoxEditor" ) );
		assertTrue( comboBox.getRenderer().getClass().getName().contains( "LookupComboBoxRenderer" ) );

		popupList = ( (BasicComboPopup) comboBox.getUI().getAccessibleChild( comboBox, 0 ) ).getList();
		assertEquals( "", ( (JLabel) comboBox.getRenderer().getListCellRendererComponent( popupList, null, 0, false, false ) ).getText() );
		assertEquals( "FOO", ( (JLabel) comboBox.getRenderer().getListCellRendererComponent( popupList, MockEnum.FOO, 1, false, false ) ).getText() );
		assertEquals( "Foo Label", ( (JLabel) comboBox.getRenderer().getListCellRendererComponent( popupList, "FOO", 1, false, false ) ).getText() );
		assertEquals( "Bar Label", ( (JLabel) comboBox.getRenderer().getListCellRendererComponent( popupList, "BAR", 2, false, false ) ).getText() );
		assertEquals( "Baz Label", ( (JLabel) comboBox.getRenderer().getListCellRendererComponent( popupList, "BAZ", 3, false, false ) ).getText() );

		editor = (BasicComboBoxEditor) comboBox.getEditor();
		editor.setItem( null );
		assertEquals( "", editor.getItem() );
		editor.setItem( MockEnum.FOO );
		assertEquals( "", editor.getItem() );
		editor.setItem( "FOO" );
		assertEquals( "Foo Label", editor.getItem() );
		editor.setItem( "BAR" );
		assertEquals( "Bar Label", editor.getItem() );
		editor.setItem( "BAZ" );
		assertEquals( "Baz Label", editor.getItem() );

		// JSpinner

		attributes.remove( LOOKUP );

		// bytes

		attributes.remove( MAXIMUM_VALUE );
		attributes.put( MINIMUM_VALUE, "2" );
		attributes.put( TYPE, byte.class.getName() );

		JSpinner spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertTrue( ( (byte) 2 ) == (Byte) ( (SpinnerNumberModel) spinner.getModel() ).getMinimum() );
		assertTrue( ( (byte) 2 ) == (Byte) spinner.getValue() );

		attributes.remove( MINIMUM_VALUE );
		attributes.put( MAXIMUM_VALUE, "99" );
		spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertTrue( ( (byte) 99 ) == (Byte) ( (SpinnerNumberModel) spinner.getModel() ).getMaximum() );

		// shorts

		attributes.remove( MAXIMUM_VALUE );
		attributes.put( MINIMUM_VALUE, "3" );
		attributes.put( TYPE, short.class.getName() );

		spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertTrue( ( (short) 3 ) == (Short) ( (SpinnerNumberModel) spinner.getModel() ).getMinimum() );
		assertTrue( ( (short) 3 ) == (Short) spinner.getValue() );

		attributes.remove( MINIMUM_VALUE );
		attributes.put( MAXIMUM_VALUE, "98" );
		spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertTrue( ( (short) 98 ) == (Short) ( (SpinnerNumberModel) spinner.getModel() ).getMaximum() );

		// ints

		attributes.remove( MAXIMUM_VALUE );
		attributes.put( MINIMUM_VALUE, "4" );
		attributes.put( TYPE, int.class.getName() );

		spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertTrue( 4 == (Integer) ( (SpinnerNumberModel) spinner.getModel() ).getMinimum() );
		assertTrue( 4 == (Integer) spinner.getValue() );
		assertEquals( 0, ( (JSpinner.DefaultEditor) spinner.getEditor() ).getTextField().getColumns() );

		attributes.remove( MINIMUM_VALUE );
		attributes.put( MAXIMUM_VALUE, "97" );
		spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertTrue( 97 == (Integer) ( (SpinnerNumberModel) spinner.getModel() ).getMaximum() );

		// longs

		attributes.remove( MAXIMUM_VALUE );
		attributes.put( MINIMUM_VALUE, "5" );
		attributes.put( TYPE, long.class.getName() );

		spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertTrue( ( (long) 5 ) == (Long) ( (SpinnerNumberModel) spinner.getModel() ).getMinimum() );
		assertTrue( ( (long) 5 ) == (Long) spinner.getValue() );
		assertEquals( 0, ( (JSpinner.DefaultEditor) spinner.getEditor() ).getTextField().getColumns() );

		attributes.remove( MINIMUM_VALUE );
		attributes.put( MAXIMUM_VALUE, "96" );
		spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertTrue( ( (long) 96 ) == (Long) ( (SpinnerNumberModel) spinner.getModel() ).getMaximum() );

		// floats

		attributes.remove( MAXIMUM_VALUE );
		attributes.put( MINIMUM_VALUE, "1.6" );
		attributes.put( TYPE, float.class.getName() );

		spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertEquals( 1.6f, spinner.getValue() );
		assertEquals( 0.1f, ( (SpinnerNumberModel) spinner.getModel() ).getStepSize() );
		assertEquals( 0, ( (JSpinner.DefaultEditor) spinner.getEditor() ).getTextField().getColumns() );

		attributes.put( MAXIMUM_FRACTIONAL_DIGITS, "3" );
		spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertEquals( 0.001d, ( (SpinnerNumberModel) spinner.getModel() ).getStepSize() );
		assertEquals( 3, ( (JSpinner.NumberEditor) spinner.getEditor() ).getFormat().getMaximumFractionDigits() );

		attributes.put( MINIMUM_VALUE, "-1.6" );
		spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertEquals( 0f, spinner.getValue() );

		attributes.remove( MINIMUM_VALUE );
		attributes.put( MAXIMUM_VALUE, "-1" );
		spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertEquals( -1f, spinner.getValue() );

		// doubles

		attributes.put( TYPE, double.class.getName() );
		attributes.remove( MAXIMUM_VALUE );
		attributes.put( MINIMUM_VALUE, "1.6" );
		spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertEquals( 1.6d, spinner.getValue() );
		assertEquals( 1000, Math.round( ( (Double) ( (SpinnerNumberModel) spinner.getModel() ).getStepSize() ) * 1000000 ) );
		assertEquals( 0, ( (JSpinner.DefaultEditor) spinner.getEditor() ).getTextField().getColumns() );

		attributes.put( MINIMUM_VALUE, "-1.6" );
		spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertEquals( 0d, spinner.getValue() );

		attributes.remove( MINIMUM_VALUE );
		attributes.put( MAXIMUM_VALUE, "-1" );
		spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertEquals( -1d, spinner.getValue() );

		attributes.put( MINIMUM_FRACTIONAL_DIGITS, "2" );
		spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertEquals( 2, ( (JSpinner.NumberEditor) spinner.getEditor() ).getFormat().getMinimumFractionDigits() );

		attributes.put( MINIMUM_INTEGER_DIGITS, "4" );
		spinner = (JSpinner) widgetBuilder.buildWidget( PROPERTY, attributes, null );
		assertEquals( 4, ( (JSpinner.NumberEditor) spinner.getEditor() ).getFormat().getMinimumIntegerDigits() );

		// Collections

		// ...of unknown type, null value

		attributes.put( TYPE, Collection.class.getName() );

		JTable table = (JTable) ( (JScrollPane) widgetBuilder.buildWidget( PROPERTY, attributes, metawidget ) ).getViewport().getView();
		CollectionTableModel<?> model = (CollectionTableModel<?>) table.getModel();
		assertEquals( "", model.getColumnName( 0 ) );
		assertEquals( 1, model.getColumnCount() );

		// ...of top-level unknown type, null value

		table = (JTable) ( (JScrollPane) widgetBuilder.buildWidget( ENTITY, attributes, metawidget ) ).getViewport().getView();
		model = (CollectionTableModel<?>) table.getModel();
		assertEquals( "", model.getColumnName( 0 ) );
		assertEquals( 1, model.getColumnCount() );

		// ...of top-level unknown type, concrete value

		List<Bar> list = new ArrayList<Bar>();
		metawidget.setToInspect( list );
		table = (JTable) ( (JScrollPane) widgetBuilder.buildWidget( ENTITY, attributes, metawidget ) ).getViewport().getView();
		model = (CollectionTableModel<?>) table.getModel();
		assertEquals( "", model.getColumnName( 0 ) );
		assertEquals( 1, model.getColumnCount() );

		// ...of top-level unknown type, concrete value with an entry

		list.add( new Bar() );
		table = (JTable) ( (JScrollPane) widgetBuilder.buildWidget( ENTITY, attributes, metawidget ) ).getViewport().getView();
		model = (CollectionTableModel<?>) table.getModel();
		assertEquals( "Firstname", model.getColumnName( 0 ) );
		assertEquals( "Surname", model.getColumnName( 1 ) );
		assertEquals( 2, model.getColumnCount() );

		// Bar includes an action. This should be ignored

		attributes.put( NAME, "bar" );
		attributes.put( PARAMETERIZED_TYPE, Bar.class.getName() );

		table = (JTable) ( (JScrollPane) widgetBuilder.buildWidget( PROPERTY, attributes, metawidget ) ).getViewport().getView();
		model = (CollectionTableModel<?>) table.getModel();
		assertEquals( "Firstname", model.getColumnName( 0 ) );
		assertEquals( "Surname", model.getColumnName( 1 ) );
		assertEquals( 2, model.getColumnCount() );
		assertEquals( 0, model.getRowCount() );

		Foo foo = new Foo();
		foo.addBar( new Bar() );
		metawidget.setToInspect( foo );

		table = (JTable) ( (JScrollPane) widgetBuilder.buildWidget( PROPERTY, attributes, metawidget ) ).getViewport().getView();
		model = (CollectionTableModel<?>) table.getModel();
		assertEquals( "Firstname", model.getColumnName( 0 ) );
		assertEquals( "Surname", model.getColumnName( 1 ) );
		assertEquals( 2, model.getColumnCount() );
		assertEquals( 1, model.getRowCount() );
	}

	public void testConfig() {

		MetawidgetTestUtils.testEqualsAndHashcode( SwingWidgetBuilderConfig.class, new SwingWidgetBuilderConfig() {
			// Subclass
		} );
	}

	//
	// Inner class
	//

	/* package private */enum MockEnum {

		FOO,
		BAR,
		BAZ
	}

	/* package private */class MockBinding
		implements BindingConverter, WidgetProcessor<JComponent, SwingMetawidget> {

		public JComponent processWidget( JComponent widget, String elementName, Map<String, String> attributes, SwingMetawidget metawidget ) {

			return widget;
		}

		public Object convertFromString( String value, Class<?> expectedType ) {

			return MockEnum.valueOf( value );
		}
	}

	public static class Foo {

		private Collection<Bar> mCollection = CollectionUtils.newHashSet();

		//
		// Public methods
		//

		public Collection<Bar> getBar() {

			return mCollection;
		}

		public void addBar( Bar bar ) {

			mCollection.add( bar );
		}
	}

	public static class Bar {

		public String getFirstname() {

			return null;
		}

		public String getSurname() {

			return null;
		}

		@UiAction
		public void doAction() {

			// Do nothing
		}

		@UiHidden
		public String getHidden() {

			return null;
		}
	}
}

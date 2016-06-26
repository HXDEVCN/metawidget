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

package org.metawidget.swing.widgetprocessor.binding.beansbinding;

import static org.metawidget.inspector.InspectionResultConstants.*;

import java.awt.Component;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JScrollPane;

import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.Binding.SyncFailure;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.beansbinding.Converter;
import org.metawidget.swing.SwingMetawidget;
import org.metawidget.swing.widgetprocessor.binding.BindingConverter;
import org.metawidget.util.ClassUtils;
import org.metawidget.util.CollectionUtils;
import org.metawidget.util.WidgetBuilderUtils;
import org.metawidget.util.simple.ObjectUtils;
import org.metawidget.util.simple.PathUtils;
import org.metawidget.util.simple.StringUtils;
import org.metawidget.widgetprocessor.iface.AdvancedWidgetProcessor;
import org.metawidget.widgetprocessor.iface.WidgetProcessorException;

/**
 * Property binding implementation based on BeansBinding (JSR 295).
 * <p>
 * This implementation recognizes the following <code>SwingMetawidget.setParameter</code>
 * parameters:
 * <p>
 * <ul>
 * <li><code>UpdateStrategy.class</code> - as defined by
 * <code>org.jdesktop.beansbinding.AutoBinding.UpdateStrategy</code>. Defaults to
 * <code>READ_ONCE</code>. If set to <code>READ</code> or <code>READ_WRITE</code>, the object being
 * inspected must provide <code>PropertyChangeSupport</code>. If set to <code>READ</code>, there is
 * no need to call <code>BeansBindingProcessor.rebind</code>. If set to <code>READ_WRITE</code>,
 * there is no need to call <code>BeansBindingProcessor.save</code>.
 * </ul>
 * <p>
 * Note: <code>BeansBinding</code> does not bind <em>actions</em>, such as invoking a method when a
 * <code>JButton</code> is pressed. For that, see <code>ReflectionBindingProcessor</code> and
 * <code>MetawidgetActionStyle</code> or <code>SwingAppFrameworkActionStyle</code>.
 *
 * @author <a href="http://kennardconsulting.com">Richard Kennard</a>
 */

public class BeansBindingProcessor
	implements AdvancedWidgetProcessor<JComponent, SwingMetawidget>, BindingConverter {

	//
	// Private members
	//

	private final UpdateStrategy							mUpdateStrategy;

	private final Map<ConvertFromTo<?, ?>, Converter<?, ?>>	mConverters	= CollectionUtils.newHashMap();

	//
	// Constructor
	//

	public BeansBindingProcessor() {

		this( new BeansBindingProcessorConfig() );
	}

	public BeansBindingProcessor( BeansBindingProcessorConfig config ) {

		mUpdateStrategy = config.getUpdateStrategy();

		// Default converters

		registerConverter( Byte.class, String.class, new NumberConverter<Byte>( Byte.class ) );
		registerConverter( Short.class, String.class, new NumberConverter<Short>( Short.class ) );
		registerConverter( Integer.class, String.class, new NumberConverter<Integer>( Integer.class ) );
		registerConverter( Long.class, String.class, new NumberConverter<Long>( Long.class ) );
		registerConverter( Float.class, String.class, new NumberConverter<Float>( Float.class ) );
		registerConverter( Double.class, String.class, new NumberConverter<Double>( Double.class ) );
		registerConverter( Boolean.class, String.class, new BooleanConverter() );

		// Custom converters (defensive copy)

		if ( config.getConverters() != null ) {
			mConverters.putAll( config.getConverters() );
		}
	}

	//
	// Public methods
	//

	public void onStartBuild( SwingMetawidget metawidget ) {

		State state = getState( metawidget );

		if ( state.getBindings() != null ) {
			for ( org.jdesktop.beansbinding.Binding<?, ?, ? extends Component, ?> binding : state.getBindings() ) {
				binding.unbind();
			}
		}

		metawidget.putClientProperty( BeansBindingProcessor.class, null );
	}

	public JComponent processWidget( JComponent component, String elementName, Map<String, String> attributes, SwingMetawidget metawidget ) {

		JComponent componentToBind = component;

		// Unwrap JScrollPanes (for JTextAreas etc)

		if ( componentToBind instanceof JScrollPane ) {
			componentToBind = (JComponent) ( (JScrollPane) componentToBind ).getViewport().getView();
		}

		// Nested Metawidgets are not bound, only remembered

		if ( componentToBind instanceof SwingMetawidget ) {

			State state = getState( metawidget );
			state.addNestedMetawidget( (SwingMetawidget) component );
			return component;
		}

		typesafeAdd( componentToBind, elementName, attributes, metawidget );

		return component;
	}

	/**
	 * Rebinds the Metawidget to the given Object.
	 * <p>
	 * This method is an optimization that allows clients to load a new object into the binding
	 * <em>without</em> calling setToInspect, and therefore without reinspecting the object or
	 * recreating the components. It is the client's responsbility to ensure the rebound object is
	 * compatible with the original setToInspect.
	 */

	public void rebind( Object toRebind, SwingMetawidget metawidget ) {

		metawidget.updateToInspectWithoutInvalidate( toRebind );
		State state = getState( metawidget );

		// Our bindings

		if ( state.getBindings() != null ) {
			for ( org.jdesktop.beansbinding.Binding<Object, ?, ? extends Component, ?> binding : state.getBindings() ) {
				binding.unbind();
				binding.setSourceObject( toRebind );
				binding = processBinding( binding, metawidget );

				if ( binding == null ) {
					continue;
				}

				binding.bind();

				SyncFailure failure = binding.refresh();

				if ( failure != null ) {
					throw WidgetProcessorException.newException( failure.getType().toString() );
				}
			}
		}

		// Nested Metawidgets

		if ( state.getNestedMetawidgets() != null ) {
			for ( SwingMetawidget nestedMetawidget : state.getNestedMetawidgets() ) {
				rebind( toRebind, nestedMetawidget );
			}
		}
	}

	public void save( SwingMetawidget metawidget ) {

		if ( UpdateStrategy.READ_WRITE.equals( mUpdateStrategy ) ) {
			throw WidgetProcessorException.newException( "Should not call save() when using " + UpdateStrategy.READ_WRITE );
		}

		State state = getState( metawidget );

		// Our bindings

		if ( state.getBindings() != null ) {
			for ( org.jdesktop.beansbinding.Binding<Object, ?, ? extends Component, ?> binding : state.getBindings() ) {
				Object sourceObject = binding.getSourceObject();
				@SuppressWarnings( "unchecked" )
				BeanProperty<Object, Object> sourceProperty = (BeanProperty<Object, Object>) binding.getSourceProperty();

				if ( !sourceProperty.isWriteable( sourceObject ) ) {
					continue;
				}

				if ( binding.getConverter() instanceof ReadOnlyToStringConverter<?> ) {
					continue;
				}

				try {
					SyncFailure failure = binding.save();

					if ( failure != null ) {
						throw WidgetProcessorException.newException( failure.getConversionException() );
					}
				} catch ( ClassCastException e ) {
					throw WidgetProcessorException.newException( "When saving from " + binding.getTargetObject().getClass() + " to " + sourceProperty + " (have you used BeansBindingProcessorConfig.setConverter?)", e );
				}
			}
		}

		// Nested Metawidgets

		if ( state.getNestedMetawidgets() != null ) {
			for ( SwingMetawidget nestedMetawidget : state.getNestedMetawidgets() ) {
				save( nestedMetawidget );
			}
		}
	}

	/**
	 * This implementation attempts to re-use the registered BeansBinding converters to also convert
	 * values for <code>BindingConverter.convertFromString</code>. The latter is a one-way
	 * conversion, only needed for converting <code>lookups</code> to <code>JComboBox</code> values.
	 */

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public Object convertFromString( String value, Class<?> expectedType ) {

		if ( String.class.equals( expectedType ) ) {
			return value;
		}

		// Try converters one way round...

		Converter<String, ?> converterFromString = getConverter( String.class, expectedType );

		if ( converterFromString != null ) {
			return converterFromString.convertForward( value );
		}

		// ...and the other...

		Converter<?, String> converterToString = getConverter( expectedType, String.class );

		if ( converterToString != null ) {
			return converterToString.convertReverse( value );
		}

		// ...or try implicit conversion...
		//
		// Note: typically we can't write converters like this, because most conversion APIs don't
		// pass <code>expectedType</code>. They typically just pass the widget and the value.
		// However <code>BindingConverter</code> is our own API.

		if ( Enum.class.isAssignableFrom( expectedType ) ) {
			return Enum.valueOf( (Class<? extends Enum>) expectedType, value );
		}

		// ...or don't convert

		return value;
	}

	public void onEndBuild( SwingMetawidget metawidget ) {

		// Do nothing
	}

	//
	// Protected methods
	//

	/**
	 * Process the given Binding prior to calling <code>binding.bind()</code>. Source, target and
	 * converters will have already been initialized.
	 * <p>
	 * Clients can subclass this <code>WidgetProcessor</code> and override this method to manipulate
	 * the binding, for example to add a <code>BindingListener</code>.
	 *
	 * @param metawidget
	 *            the owning Metawidget. May be useful for clients
	 * @return the processed Binding, or null to abort binding this property
	 */

	protected <S, V, T extends Component, W> Binding<S, V, T, W> processBinding( Binding<S, V, T, W> binding, SwingMetawidget metawidget ) {

		return binding;
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	protected <S, V, T extends Component, W> Binding typesafeAdd( T component, String elementName, Map<String, String> attributes, SwingMetawidget metawidget ) {

		String componentProperty = metawidget.getValueProperty( component );

		if ( componentProperty == null ) {
			return null;
		}

		// Source property

		S source = (S) metawidget.getToInspect();
		String sourceProperty = PathUtils.parsePath( metawidget.getPath() ).getNames().replace( StringUtils.SEPARATOR_FORWARD_SLASH_CHAR, StringUtils.SEPARATOR_DOT_CHAR );

		if ( PROPERTY.equals( elementName ) ) {
			if ( sourceProperty.length() > 0 ) {
				sourceProperty += StringUtils.SEPARATOR_DOT_CHAR;
			}

			sourceProperty += attributes.get( NAME );
		}

		BeanProperty<S, V> propertySource = BeanProperty.create( sourceProperty );

		Class<W> targetClass;

		// Create binding

		BeanProperty<T, W> propertyTarget = BeanProperty.create( componentProperty );

		org.jdesktop.beansbinding.Binding<S, V, T, W> binding = Bindings.createAutoBinding( mUpdateStrategy, source, propertySource, component, propertyTarget );
		targetClass = (Class<W>) propertyTarget.getWriteType( component );

		// Add a converter

		Converter<V, W> converter = getConverter( propertySource, source, targetClass, attributes );

		// Convenience converter for READ_ONLY fields (not just based on 'component instanceof
		// JLabel', because the user may override a DONT_EXPAND to be a non-editable JTextField)
		//
		// See https://sourceforge.net/projects/metawidget/forums/forum/747623/topic/3460563

		if ( converter == null && WidgetBuilderUtils.isReadOnly( attributes ) && targetClass.equals( String.class ) ) {
			converter = new ReadOnlyToStringConverter();
		}

		binding.setConverter( converter );
		binding = processBinding( binding, metawidget );

		if ( binding == null ) {
			return null;
		}

		// Bind it

		try {
			binding.bind();
		} catch ( ClassCastException e ) {
			throw WidgetProcessorException.newException( "When binding " + metawidget.getPath() + StringUtils.SEPARATOR_FORWARD_SLASH_CHAR + sourceProperty + " to " + component.getClass() + "." + componentProperty + " (have you used BeansBindingProcessorConfig.setConverter?)", e );
		}

		// Save the binding

		State state = getState( metawidget );
		state.addBinding( (org.jdesktop.beansbinding.Binding<Object, V, T, W>) binding );

		return binding;
	}

	/**
	 * Get a converter for the given source class. Clients can override this method to provide
	 * customized converter implementations.
	 *
	 * @param attributes
	 *            the attributes of the given business property. May be useful if you want to
	 *            lookup, say, 'actual-class'
	 */

	@SuppressWarnings( "unchecked" )
	protected <S, V, W> Converter<V, W> getConverter( BeanProperty<S, V> propertySource, S source, Class<W> targetClass, Map<String, String> attributes ) {

		// Determine sourceClass

		Class<V> sourceClass = null;

		if ( propertySource.isWriteable( source ) ) {
			sourceClass = (Class<V>) propertySource.getWriteType( source );
		} else if ( propertySource.isReadable( source ) ) {

			// BeansBinding does not allow us to lookup the type of a non-writable property

			V value = propertySource.getValue( source );

			if ( value != null ) {
				sourceClass = (Class<V>) value.getClass();
			} else {
				return null;
			}
		} else {
			throw WidgetProcessorException.newException( "Property '" + propertySource + "' has no getter and no setter (or parent is null)" );
		}

		return getConverter( sourceClass, targetClass );
	}

	protected State getState( SwingMetawidget metawidget ) {

		State state = (State) metawidget.getClientProperty( BeansBindingProcessor.class );

		if ( state == null ) {
			state = new State();
			metawidget.putClientProperty( BeansBindingProcessor.class, state );
		}

		return state;
	}

	//
	// Private methods
	//

	private <S, T> void registerConverter( Class<S> source, Class<T> target, Converter<S, T> converter ) {

		mConverters.put( new ConvertFromTo<S, T>( source, target ), converter );
	}

	/**
	 * Gets the Converter for the given Class (if any).
	 */

	@SuppressWarnings( "unchecked" )
	private <V, W> Converter<V, W> getConverter( Class<V> sourceClass, Class<W> targetClass ) {

		Class<V> sourceClassTraversal = sourceClass;
		Class<W> targetClassTraversal = targetClass;

		if ( sourceClassTraversal.isPrimitive() ) {
			sourceClassTraversal = (Class<V>) ClassUtils.getWrapperClass( sourceClassTraversal );
		}

		if ( targetClassTraversal.isPrimitive() ) {
			targetClassTraversal = (Class<W>) ClassUtils.getWrapperClass( targetClassTraversal );
		}

		while ( sourceClassTraversal != null ) {
			Converter<V, W> converter = (Converter<V, W>) mConverters.get( new ConvertFromTo<V, W>( sourceClassTraversal, targetClassTraversal ) );

			if ( converter != null ) {
				return converter;
			}

			sourceClassTraversal = (Class<V>) sourceClassTraversal.getSuperclass();
		}

		return null;
	}

	//
	// Inner class
	//

	/**
	 * Simple, lightweight structure for saving state.
	 */

	protected static class State {

		//
		// Private members
		//

		private Set<org.jdesktop.beansbinding.Binding<Object, ?, ? extends Component, ?>>	mBindings;

		private Set<SwingMetawidget>														mNestedMetawidgets;

		//
		// Public methods
		//

		public Set<org.jdesktop.beansbinding.Binding<Object, ?, ? extends Component, ?>> getBindings() {

			return mBindings;
		}

		public void addBinding( org.jdesktop.beansbinding.Binding<Object, ?, ? extends Component, ?> binding ) {

			if ( mBindings == null ) {
				mBindings = CollectionUtils.newHashSet();
			}

			mBindings.add( binding );
		}

		public Set<SwingMetawidget> getNestedMetawidgets() {

			return mNestedMetawidgets;
		}

		public void addNestedMetawidget( SwingMetawidget nestedMetawidgets ) {

			if ( mNestedMetawidgets == null ) {
				mNestedMetawidgets = CollectionUtils.newHashSet();
			}

			mNestedMetawidgets.add( nestedMetawidgets );
		}
	}

	/* package private */static final class ConvertFromTo<S, T> {

		//
		// Private members
		//

		private Class<S>	mSource;

		private Class<T>	mTarget;

		//
		// Constructor
		//

		public ConvertFromTo( Class<S> source, Class<T> target ) {

			mSource = source;
			mTarget = target;
		}

		//
		// Public methods
		//

		@Override
		public boolean equals( Object that ) {

			if ( this == that ) {
				return true;
			}

			if ( that == null ) {
				return false;
			}

			if ( getClass() != that.getClass() ) {
				return false;
			}

			if ( !ObjectUtils.nullSafeEquals( mSource, ( (ConvertFromTo<?, ?>) that ).mSource ) ) {
				return false;
			}

			if ( !ObjectUtils.nullSafeEquals( mTarget, ( (ConvertFromTo<?, ?>) that ).mTarget ) ) {
				return false;
			}

			return true;
		}

		@Override
		public int hashCode() {

			int hashCode = 1;
			hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode( mSource.hashCode() );
			hashCode = 31 * hashCode + ObjectUtils.nullSafeHashCode( mTarget.hashCode() );

			return hashCode;
		}
	}
}

// Metawidget
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package org.metawidget.faces.component.html.layout.richfaces;

import javax.faces.component.UIComponent;

import org.metawidget.faces.component.UIMetawidget;
import org.metawidget.layout.decorator.LayoutDecoratorConfig;
import org.metawidget.layout.iface.Layout;
import org.metawidget.util.simple.ObjectUtils;

/**
 * Configures a SimpleTogglePanelLayoutDecorator prior to use. Once instantiated, Layouts are
 * immutable.
 *
 * @author Richard Kennard
 */

public class SimpleTogglePanelLayoutDecoratorConfig
	extends LayoutDecoratorConfig<UIComponent, UIMetawidget>
{
	//
	// Private members
	//

	private String	mStyle;

	private String	mStyleClass;

	private String	mSwitchType	= "server";

	private boolean	mOpened;

	//
	// Public methods
	//

	/**
	 * Overridden to use covariant return type.
	 *
	 * @return this, as part of a fluent interface
	 */

	@Override
	public SimpleTogglePanelLayoutDecoratorConfig setLayout( Layout<UIComponent, UIMetawidget> layout )
	{
		super.setLayout( layout );

		return this;
	}

	public String getStyle()
	{
		return mStyle;
	}

	/**
	 * @return this, as part of a fluent interface
	 */

	public SimpleTogglePanelLayoutDecoratorConfig setStyle( String style )
	{
		mStyle = style;

		return this;
	}

	public String getStyleClass()
	{
		return mStyleClass;
	}

	/**
	 * @return this, as part of a fluent interface
	 */

	public SimpleTogglePanelLayoutDecoratorConfig setStyleClass( String styleClass )
	{
		mStyleClass = styleClass;

		return this;
	}

	public String getSwitchType()
	{
		return mSwitchType;
	}

	/**
	 * @return this, as part of a fluent interface
	 */

	public SimpleTogglePanelLayoutDecoratorConfig setSwitchType( String switchType )
	{
		mSwitchType = switchType;

		return this;
	}

	public boolean isOpened()
	{
		return mOpened;
	}

	/**
	 * @return this, as part of a fluent interface
	 */

	public SimpleTogglePanelLayoutDecoratorConfig setOpened( boolean opened )
	{
		mOpened = opened;

		return this;
	}

	@Override
	public boolean equals( Object that )
	{
		if ( !( that instanceof SimpleTogglePanelLayoutDecoratorConfig ) )
			return false;

		if ( !ObjectUtils.nullSafeEquals( mStyle, ( (SimpleTogglePanelLayoutDecoratorConfig) that ).mStyle ) )
			return false;

		if ( !ObjectUtils.nullSafeEquals( mStyleClass, ( (SimpleTogglePanelLayoutDecoratorConfig) that ).mStyleClass ) )
			return false;

		if ( !ObjectUtils.nullSafeEquals( mSwitchType, ( (SimpleTogglePanelLayoutDecoratorConfig) that ).mSwitchType ) )
			return false;

		if ( mOpened != ( (SimpleTogglePanelLayoutDecoratorConfig) that ).mOpened )
			return false;

		return super.equals( that );
	}

	@Override
	public int hashCode()
	{
		int hashCode = super.hashCode();
		hashCode ^= ObjectUtils.nullSafeHashCode( mStyle );
		hashCode ^= ObjectUtils.nullSafeHashCode( mStyleClass );
		hashCode ^= ObjectUtils.nullSafeHashCode( mSwitchType );
		hashCode ^= ObjectUtils.nullSafeHashCode( mOpened );

		return hashCode;
	}
}
package chromium;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface AppMenuHandler {
    @IntDef({
            AppMenuItemType.STANDARD,
            AppMenuItemType.STANDARD_NO_ICON,
            AppMenuItemType.TITLE_BUTTON,
            AppMenuItemType.BUTTON_ROW,
            AppMenuItemType.MENU_ITEM_WITH_SUBMENU,
            AppMenuItemType.MENU_ITEM_WITH_SUBMENU_NO_ICON,
            AppMenuItemType.SUBMENU_HEADER,
            AppMenuItemType.DIVIDER,
            AppMenuItemType.BOOKMARK,
            AppMenuItemType.TAB,
            AppMenuItemType.RECENT_ENTRY,
            AppMenuItemType.RECENT_ENTRY_NO_ICON,
            AppMenuItemType.EMPTY,
            AppMenuItemType.HEADER
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface AppMenuItemType {
        /** Regular Android menu item that contains a title and an icon if icon is specified. */
        int STANDARD = 0;

        /**
         * Regular Android menu item that contains only a title, without an icon or start padding.
         */
        int STANDARD_NO_ICON = 1;

        /**
         * Menu item that has two buttons, the first one is a title and the second one is an icon.
         * It is different from the regular menu item because it contains two separate buttons.
         */
        int TITLE_BUTTON = 2;

        /**
         * Menu item that has multiple buttons (no more than 5). Every one of these buttons is
         * displayed as an icon.
         */
        int BUTTON_ROW = 3;

        /** Menu item that when contains submenus. */
        int MENU_ITEM_WITH_SUBMENU = 4;

        /** Menu item that contains submenus, without an icon or start padding. */
        int MENU_ITEM_WITH_SUBMENU_NO_ICON = 5;

        /** The header for submenus when submenus are displayed in drilldown. */
        int SUBMENU_HEADER = 6;

        /** A divider item to distinguish between menu item groupings. */
        int DIVIDER = 7;

        /** A bookmark item. */
        int BOOKMARK = 8;

        /** A tab item for tab groups. */
        int TAB = 9;

        /** A recent entry item. */
        int RECENT_ENTRY = 10;

        /** A recent entry item with no icon. */
        int RECENT_ENTRY_NO_ICON = 11;

        /** An item indicating that a submenu is empty. */
        int EMPTY = 12;

        /** A header item. */
        int HEADER = 13;

        /**
         * The number of menu item types specified above. If you add a menu item type you MUST
         * increment this.
         */
        int NUM_ENTRIES = 14;
    }
}

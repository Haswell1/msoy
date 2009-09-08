//
// $Id$

package client.games;

import client.util.MediaUploader;
import client.util.MediaUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.HasAlignment;
import com.threerings.gwt.ui.SmartTable;
import com.threerings.msoy.data.all.MediaDesc;
import com.threerings.msoy.game.gwt.GameInfo;

/**
 * Utility classes and methods for editing games.
 */
public class EditorUtil
{
    /**
     * Basic box for containing media that the user can change by uploading.
     */
    public static class MediaBox extends SmartTable
        implements MediaUploader.Listener
    {
        /**
         * Creates a new media box.
         * @param size the size of the displayed media, from constants in {@link MediaDesc}
         * @param mediaId the id of the media when uploading, from constants in
         * {@link com.threerings.msoy.item.data.all.Item}
         * @param media the current media set, or null if none is set
         */
        public MediaBox (int size, String mediaId, MediaDesc media) {
            super("mediaBox", 0, 0);
            _size = size;
            setMedia(media);
            setWidget(1, 0, new MediaUploader(mediaId, this));
            getFlexCellFormatter().setVerticalAlignment(0, 2, HasAlignment.ALIGN_BOTTOM);
        }

        /**
         * Sets the media, or clears it if null is given.
         */
        public void setMedia (MediaDesc media) {
            if (media == null) {
                setText(0, 0, "");
            } else {
                setWidget(0, 0, MediaUtil.createMediaView(_media = media, _size));
            }
        }

        /**
         * Gets the currently displayed media.
         */
        public MediaDesc getMedia () {
            return _media;
        }
    
        // from MediaUploader.Listener
        public void mediaUploaded (String name, MediaDesc desc, int width, int height) {
            setMedia(desc);
            mediaModified();
        }

        /**
         * Lets this editor box know that the media was just changed as a result of a user action.
         */
        protected void mediaModified () {
        }

        protected int _size;
        protected MediaDesc _media;
    }

    /**
     * Basic box for containing code media that the user can change by uploading. This is different
     * from media box because there is nothing to view.
     */
    public static class CodeBox extends SmartTable
        implements MediaUploader.Listener
    {
        /**
         * Creates a new code media box.
         * @param emptyMessage text to show when there is no code
         * @param mediaId the id of the media when uploading, from constants in
         * {@link com.threerings.msoy.item.data.all.Item}
         * @param media the current media set, or null if none is set
         */
        public CodeBox (String emptyMessage, String mediaId, MediaDesc media) {
            super("codeBox", 0, 0);
            _emptyMessage = emptyMessage;
            setMedia(media);
            setWidget(1, 0, new MediaUploader(mediaId, this));
        }
    
        /**
         * Sets the media, or clears it if null is given.
         */
        public void setMedia (MediaDesc media) {
            _media = media;
            setText(0, 0, (media == null) ? _emptyMessage : media.toString(), 1, "Code");
        }
    
        /**
         * Gets the currently displayed media.
         */
        public MediaDesc getMedia () {
            return _media;
        }
    
        // from MediaUploader.Listener
        public void mediaUploaded (String name, MediaDesc desc, int width, int height) {
            setMedia(desc);
            mediaModified();
        }
    
        /**
         * Lets this editor box know that the media is modified.
         */
        protected void mediaModified () {
        }

        protected String _emptyMessage;
        protected MediaDesc _media;
    }

    /**
     * Exception to be thrown when an invalid user input is encountered. The contained message is
     * translated text to be shown directly to the user.
     */
    protected static class ConfigException extends RuntimeException
    {
        public ConfigException (String message) {
            super(message);
        }
    }

    /**
     * Throws a {@link ConfigException} if the name is empty or exceeds the allowed length. For
     * convenience, returns the name if all is well.
     */
    public static String checkName (String name)
    {
        if (name.length() == 0 || name.length() > GameInfo.MAX_NAME_LENGTH) {
            throw new ConfigException(_msgs.errInvalidName(""+GameInfo.MAX_NAME_LENGTH));
        }
        return name;
    }

    /**
     * Throws a {@link ConfigException} if the given media is not null and not an image. For
     * convenience, returns the media if all is well.
     */
    public static MediaDesc checkImageMedia (String type, MediaDesc desc)
    {
        if (desc != null && !desc.isImage()) {
            throw new ConfigException(_msgs.errInvalidImage(type));
        }
        return desc;
    }

    /**
     * Throws a {@link ConfigException} if the given media is null or not an image. For convenience,
     * returns the media if all is well.
     */
    public static MediaDesc requireImageMedia (String type, MediaDesc desc)
    {
        if (desc == null || !desc.isImage()) {
            throw new ConfigException(_msgs.errInvalidImage(type));
        }
        return desc;
    }

    /**
     * Throws a {@link ConfigException} if the given media is null or not appropriate flash media.
     * For convenience, returns the media if all is well.
     */
    public static MediaDesc checkClientMedia (MediaDesc desc)
    {
        if (desc == null || !desc.isSWF()) {
            throw new ConfigException(_msgs.errInvalidClientCode());
        }
        return desc;
    }

    /**
     * Throws a {@link ConfigException} if the given media is null or not appropriate thane media.
     * For convenience, returns the media if all is well.
     */
    public static MediaDesc checkServerMedia (MediaDesc desc)
    {
        if (desc != null && desc.mimeType != MediaDesc.COMPILED_ACTIONSCRIPT_LIBRARY) {
            throw new ConfigException(_msgs.errInvalidServerCode());
        }
        return desc;
    }

    protected static final GamesMessages _msgs = GWT.create(GamesMessages.class);
}
